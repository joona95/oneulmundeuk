package app.oneulmundeuk

import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.RecordEmbeddingEntity
import app.oneulmundeuk.data.db.RecordTextRow
import app.oneulmundeuk.related.E5RecordEmbeddingRuntime
import app.oneulmundeuk.related.EmbedderLoader
import app.oneulmundeuk.related.EmbeddingCodec
import app.oneulmundeuk.related.LoadedEmbedder
import app.oneulmundeuk.related.RecordEmbeddingCache
import app.oneulmundeuk.related.RecordEmbeddingStorage
import app.oneulmundeuk.related.RelatedText
import app.oneulmundeuk.related.TextEmbedder
import app.oneulmundeuk.related.e5.E5Embedder
import app.oneulmundeuk.related.e5.E5Purpose
import app.oneulmundeuk.related.model.RelatedModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

/**
 * M6-9 step 1: production record embeddings (gate → queue → lazy e5 load → cache) on the JVM. The model is a fake
 * behind [EmbedderLoader]; the e5 prefix / space ids are the real ones.
 */
class RecordEmbeddingRuntimeTest {
    private val related = E5Embedder.modelIdFor(E5Purpose.RELATED)

    /** [FakeAnalysisStorage] (queue + per-space embeddings) seen through the embedding step's interface. */
    private class Storage(val inner: FakeAnalysisStorage = FakeAnalysisStorage()) : RecordEmbeddingStorage {
        override suspend fun pendingIds(limit: Int): List<String> =
            inner.analyses.values.filter { it.status == AnalysisStatus.PENDING }
                .sortedWith(compareBy({ it.queuedAt }, { it.recordId })).take(limit).map { it.recordId }
        override suspend fun record(recordId: String): RecordTextRow? = inner.records[recordId]
        override suspend fun embedding(recordId: String, modelId: String) = inner.embedding(recordId, modelId)
        override suspend fun saveEmbedding(embedding: RecordEmbeddingEntity) = inner.saveEmbedding(embedding)
    }

    private val storage = Storage()
    private val gate = MutableStateFlow(false)
    private var loads = 0
    private var closes = 0
    private var failLoad = false
    private val model = FakeEmbedder(related) { text -> floatArrayOf(text.length.toFloat(), 1f) }

    /** Loads [model] presented as the space [spaceId] (a real loader returns e5's view for that space). */
    private fun loader(spaceId: String) = EmbedderLoader {
        if (failLoad) error("model file missing")
        loads++
        LoadedEmbedder(object : TextEmbedder by model { override val modelId = spaceId }) { closes++ }
    }

    private fun runtime(scope: CoroutineScope, modelId: String = related) =
        E5RecordEmbeddingRuntime(storage, gate, prepare = {}, relatedModelId = modelId, loader = loader(modelId), scope = scope, now = { 42L })

    private fun queue(id: String, text: String, at: Long) {
        storage.inner.add(id, text, at)
        storage.inner.enqueue(id)
    }

    /**
     * Where the runtime's own coroutines (gate collector, passes) run: the test scheduler, as FOREGROUND work.
     * Not `backgroundScope` — since kotlinx-coroutines-test 1.7, advanceUntilIdle() stops once only background tasks are
     * left, so a pass launched there would never run before the assertions. The collector suspends forever; [cancelRuntimes]
     * stops it after each test.
     */
    private val runtimeScopes = mutableListOf<CoroutineScope>()

    private fun TestScope.runtimeScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()).also { runtimeScopes += it }

    @After
    fun cancelRuntimes() = runtimeScopes.forEach { it.cancel() }

    private fun TestScope.started(): E5RecordEmbeddingRuntime = runtime(runtimeScope()).also { it.start(); advanceUntilIdle() }

    @Test
    fun e5SpacesKeepTheBenchmarkPrefixesApart() {
        assertEquals("query: " to "query: ", E5Purpose.RELATED.recordPrefix to E5Purpose.RELATED.queryPrefix) // M5
        assertEquals("passage: " to "query: ", E5Purpose.EXPLORE.recordPrefix to E5Purpose.EXPLORE.queryPrefix) // S1
        val explore = E5Embedder.modelIdFor(E5Purpose.EXPLORE)
        assertNotEquals(related, explore)
        listOf(related, explore).forEach { assertTrue(it, it.startsWith("${RelatedModels.E5.id}@${RelatedModels.E5.version}|")) }
    }

    @Test
    fun gateClosedMeansNothingIsQueuedLoadedOrEmbedded() = runTest {
        queue("a", "기록", 1)
        val rt = started()
        assertFalse(rt.analysisEnabled)
        rt.onRecordsChanged()
        advanceUntilIdle()
        assertEquals(0, loads)
        assertTrue(model.calls.isEmpty())
        assertTrue(storage.inner.embeddings.isEmpty())
        assertNull(rt.activePipelineVersion) // no judge yet: nothing shown
        assertNull(rt.textEmbedder) // Explore not connected
    }

    @Test
    fun gateOpenEmbedsQueuedTargetsInTheRelatedSpaceOnceAndClosesTheModel() = runTest {
        queue("a", "첫 기록", 1)
        queue("b", "둘째 기록이다", 2)
        storage.inner.add("x", "큐에 없는 기록", 3) // not an analysis target → not embedded
        val rt = started()
        gate.value = true
        advanceUntilIdle()
        assertTrue(rt.analysisEnabled)
        assertEquals(listOf("첫 기록", "둘째 기록이다"), model.calls)
        assertEquals(1 to 1, loads to closes) // loaded once for the pass, closed after it
        val row = storage.inner.embeddings.getValue("b" to related)
        assertEquals(RelatedText.hash("둘째 기록이다"), row.textHash)
        assertArrayEquals(floatArrayOf(7f, 1f), EmbeddingCodec.decode(row.vector, row.dim), 0f)
        assertNull(storage.inner.embeddings["x" to related])
        assertEquals(AnalysisStatus.PENDING, storage.inner.status("a")) // queue untouched: the judge step finishes it

        // next change: everything cached → the model is not even loaded
        rt.onRecordsChanged()
        advanceUntilIdle()
        assertEquals(1, loads)
        assertEquals(2, model.calls.size)
    }

    @Test
    fun staleTextModelOrPurposeIsNeverReused() = runTest {
        queue("a", "원래 글", 1)
        gate.value = true
        val rt = started() // gate already open → first pass embeds "a"
        assertEquals(1, rt.embedPending().reused)

        storage.inner.edit("a", "고친 글") // text changed (the invalidator would also re-queue it)
        assertEquals(1, rt.embedPending().embedded)
        assertEquals(RelatedText.hash("고친 글"), storage.inner.embeddings.getValue("a" to related).textHash)

        // a row of another space for the same record + text is never read as a Related vector
        val explore = E5Embedder.modelIdFor(E5Purpose.EXPLORE)
        storage.inner.add("b", "다른 기록", 2)
        storage.inner.saveEmbedding(RecordEmbeddingEntity("b", explore, RelatedText.hash("다른 기록"), 2, EmbeddingCodec.encode(floatArrayOf(9f, 9f)), 1))
        storage.inner.enqueue("b")
        assertEquals(1, rt.embedPending().embedded)
        assertNotNull(storage.inner.embeddings["b" to explore]) // both spaces kept
        assertArrayEquals(floatArrayOf(5f, 1f), EmbeddingCodec.decode(storage.inner.embeddings.getValue("b" to related).vector, 2), 0f)

        // a new model artifact version = a new space: both re-embedded, old rows not reused
        model.calls.clear()
        runtime(runtimeScope(), modelId = "multilingual-e5-small-ko-v2@next|related-q-q").start()
        advanceUntilIdle()
        assertEquals(listOf("고친 글", "다른 기록"), model.calls)
    }

    @Test
    fun embeddingOrLoadFailureEndsThePassQuietlyAndRetriesLater() = runTest {
        queue("a", "하나", 1)
        queue("b", "둘", 2)
        val rt = runtime(runtimeScope())
        gate.value = true
        rt.start(); advanceUntilIdle()
        storage.inner.embeddings.clear()

        model.failOnCall = model.calls.size + 2 // "b" fails
        val failed = rt.embedPending()
        assertNotNull(failed.error)
        assertEquals(1, failed.embedded)
        assertEquals(AnalysisStatus.PENDING, storage.inner.status("b")) // nothing marked FAILED
        assertEquals(loads, closes) // the model is closed even after a failure

        failLoad = true
        storage.inner.embeddings.clear()
        assertNotNull(rt.embedPending().error) // missing files → no crash
        failLoad = false
        model.failOnCall = null
        val retry = rt.embedPending()
        assertNull(retry.error)
        assertEquals(2, retry.embedded)
    }

    @Test
    fun gateClosingStopsThePassBeforeTheNextRecord() = runTest {
        queue("a", "하나", 1)
        queue("b", "둘", 2)
        val rt = runtime(runtimeScope())
        gate.value = true
        rt.start(); advanceUntilIdle()
        storage.inner.embeddings.clear()
        gate.value = false // 관련된 생각 OFF / models deleted
        advanceUntilIdle()
        assertFalse(rt.analysisEnabled)
        val callsBefore = model.calls.size
        assertEquals(0, rt.embedPending().embedded)
        assertEquals(callsBefore, model.calls.size)
    }

    @Test
    fun cacheRejectsMalformedRows() = runTest {
        val record = RecordTextRow("a", "글", 1)
        storage.inner.records["a"] = record
        storage.inner.saveEmbedding(RecordEmbeddingEntity("a", related, RelatedText.hash("글"), 3, ByteArray(4), 1)) // dim ≠ bytes
        val cache = RecordEmbeddingCache(storage, model)
        assertNull(cache.cached(record))
        assertArrayEquals(floatArrayOf(1f, 1f), cache.vectorFor(record), 0f)
        assertNotNull(cache.cached(record))
    }
}
