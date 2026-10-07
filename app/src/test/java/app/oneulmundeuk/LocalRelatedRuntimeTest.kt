package app.oneulmundeuk

import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.JudgmentStatus
import app.oneulmundeuk.related.AnalysisOutcome
import app.oneulmundeuk.related.JudgeResult
import app.oneulmundeuk.related.LoadedModel
import app.oneulmundeuk.related.LocalRelatedRuntime
import app.oneulmundeuk.related.ModelLoader
import app.oneulmundeuk.related.RelatedAnalysisStorage
import app.oneulmundeuk.related.RelatedPipeline
import app.oneulmundeuk.related.RelatedRuntimeStorage
import app.oneulmundeuk.related.RelatedValueJudge
import app.oneulmundeuk.related.TextEmbedder
import app.oneulmundeuk.related.e5.E5Embedder
import app.oneulmundeuk.related.e5.E5Purpose
import app.oneulmundeuk.related.model.RelatedModels
import app.oneulmundeuk.related.qwen.QwenJudge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * M6-7 production runtime on the JVM: gate → queue → lazy e5 (RELATED space) → Top 30 → lazy Qwen judge → DONE.
 * Models are fakes behind [ModelLoader]; the analyzer, cache rules, version ids and failure policy are the real ones.
 * Texts carry what the fakes answer: "s=0.80" → cosine 0.80 to a target, "l=2" → judge label.
 */
class LocalRelatedRuntimeTest {
    private val e5Id = E5Embedder.modelIdFor(E5Purpose.RELATED)
    private val qwenId = QwenJudge.MODEL_ID

    /** [FakeAnalysisStorage] + the two runtime operations, with the DAO's guards. */
    private class Storage(val inner: FakeAnalysisStorage = FakeAnalysisStorage()) : RelatedRuntimeStorage, RelatedAnalysisStorage by inner {
        override suspend fun resetRunningToPending(now: Long): Int {
            val running = inner.analyses.values.filter { it.status == AnalysisStatus.RUNNING }
            running.forEach { inner.analyses[it.recordId] = it.copy(status = AnalysisStatus.PENDING) }
            return running.size
        }

        override suspend fun requeueFailed(maxAttempts: Int, now: Long): Int {
            val failed = inner.analyses.values.filter { it.status == AnalysisStatus.FAILED && it.attempts < maxAttempts }
            failed.forEach { inner.analyses[it.recordId] = it.copy(status = AnalysisStatus.PENDING) }
            return failed.size
        }
    }

    private val storage = Storage()
    private val gate = MutableStateFlow(false)
    private val e5 = FakeEmbedder(e5Id) { text ->
        val s = Regex("s=([0-9.]+)").find(text)?.groupValues?.get(1)?.toFloat() ?: return@FakeEmbedder floatArrayOf(1f, 0f)
        floatArrayOf(s, sqrt(1 - s * s))
    }
    private val qwen = FakeJudge(qwenId) { _, past -> JudgeResult.Label(Regex("l=(\\d)").find(past)?.groupValues?.get(1)?.toInt() ?: 0) }
    private var e5Loads = 0
    private var e5Closes = 0
    private var qwenLoads = 0
    private var qwenCloses = 0
    private var failQwenLoad = false
    private var activeJudgments = 0
    private var maxActiveJudgments = 0
    private var judgeDelayMs = 0L

    private val runtimeScopes = mutableListOf<CoroutineScope>()

    /** The runtime's own coroutines run on the test scheduler as foreground work (advanceUntilIdle runs them). */
    private fun TestScope.runtimeScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()).also { runtimeScopes += it }

    @After
    fun cancelRuntimes() = runtimeScopes.forEach { it.cancel() }

    private fun runtime(scope: CoroutineScope) = LocalRelatedRuntime(
        storage = storage, gate = gate, prepare = {}, embedderModelId = e5Id, judgeModelId = qwenId,
        embedderLoader = ModelLoader { e5Loads++; LoadedModel<TextEmbedder>(e5) { e5Closes++ } },
        judgeLoader = ModelLoader {
            if (failQwenLoad) error("qwen model not installed")
            qwenLoads++
            LoadedModel<RelatedValueJudge>(object : RelatedValueJudge {
                override val modelId = qwenId
                override suspend fun judge(current: String, past: String): JudgeResult {
                    activeJudgments++
                    maxActiveJudgments = maxOf(maxActiveJudgments, activeJudgments)
                    try {
                        if (judgeDelayMs > 0) delay(judgeDelayMs)
                        return qwen.judge(current, past)
                    } finally {
                        activeJudgments--
                    }
                }
            }) { qwenCloses++ }
        },
        scope = scope, now = { 42L },
    )

    private fun TestScope.started(): LocalRelatedRuntime = runtime(runtimeScope()).also { it.start(); advanceUntilIdle() }

    /** Past records c01.. at t = 1, 2, …, then the target "now" queued (as `RelatedInvalidator` does on save). */
    private fun setUp(vararg past: String, target: String = "지금 기록", at: Long = 100): String {
        past.forEachIndexed { i, text -> storage.inner.add("c%02d".format(i + 1), text, i + 1L) }
        storage.inner.add("now", target, at)
        storage.inner.enqueue("now")
        return "now"
    }

    @Test
    fun versionSeparatesEmbeddingSpaceFromJudge() {
        val rt = runtime(CoroutineScope(SupervisorJob()).also { runtimeScopes += it })
        assertEquals(RelatedPipeline.version(e5Id, qwenId), rt.activePipelineVersion)
        assertTrue("e=$e5Id|" in rt.activePipelineVersion && "j=$qwenId|" in rt.activePipelineVersion)
        assertTrue(e5Id.startsWith("${RelatedModels.E5.id}@${RelatedModels.E5.version}|related-q-q")) // M6-9 cache space
        assertNull(rt.textEmbedder) // Explore not connected
    }

    @Test
    fun gateClosed_nothingQueuedLoadedOrJudged() = runTest {
        setUp("c s=0.9 l=2")
        val rt = started()
        assertFalse(rt.analysisEnabled) // RelatedInvalidator queues nothing on save
        rt.onRecordsChanged()
        advanceUntilIdle()
        assertEquals(0, e5Loads + qwenLoads)
        assertTrue(e5.calls.isEmpty() && qwen.calls.isEmpty())
        assertEquals(AnalysisStatus.PENDING, storage.inner.status("now"))
        assertTrue(rt.drain().outcomes.isEmpty()) // even a direct pass does nothing while closed
    }

    @Test
    fun gateOpen_e5ThenQwenThenDone_label2OnlyInSimilarityOrder() = runTest {
        setUp("관련 s=0.90 l=2", "애매 s=0.95 l=1", "무관 s=0.99 l=0", "덜 관련 s=0.80 l=2")
        val rt = started()
        gate.value = true
        advanceUntilIdle()
        assertTrue(rt.analysisEnabled)
        assertEquals(AnalysisStatus.DONE, storage.inner.status("now"))
        val judged = storage.inner.judgments("now")
        assertEquals(4, judged.size)
        assertTrue(judged.all { it.pipelineVersion == rt.activePipelineVersion && it.status == JudgmentStatus.OK })
        // what RelatedStore will show: label 2 only, similarity DESC — label 0 / 1 never
        val shown = judged.filter { it.label == 2 }.sortedByDescending { it.similarity }.map { it.candidateId }
        assertEquals(listOf("c01", "c04"), shown)
        // RELATED cache rows in the M6-9 space
        assertTrue(storage.inner.embeddings.keys.all { it.second == e5Id })
        assertEquals(5, storage.inner.embeddings.size)
        assertEquals(1 to 1, e5Loads to e5Closes)
        assertEquals(1 to 1, qwenLoads to qwenCloses) // loaded once per pass, always closed
    }

    @Test
    fun noEarlierRecord_doneWithoutLoadingQwen() = runTest {
        setUp()
        gate.value = true
        started()
        assertEquals(AnalysisStatus.DONE, storage.inner.status("now"))
        assertEquals(0, qwenLoads)
    }

    @Test
    fun e5CacheIsReused_onlyNewTextsAreEmbedded() = runTest {
        setUp("a s=0.9 l=2", "b s=0.8 l=0")
        gate.value = true
        val rt = started()
        assertEquals(3, e5.calls.size)
        storage.inner.add("next", "다음 기록", 200)
        storage.inner.enqueue("next")
        e5.calls.clear()
        rt.onRecordsChanged()
        advanceUntilIdle()
        assertEquals(listOf("다음 기록"), e5.calls) // c01, c02, now: same text + model + purpose → no re-embedding
        assertEquals(AnalysisStatus.DONE, storage.inner.status("next"))

        // the same pair is not judged again either (judgment cache), and a DONE target is left alone
        qwen.calls.clear()
        rt.onRecordsChanged()
        advanceUntilIdle()
        assertTrue(qwen.calls.isEmpty())
    }

    @Test
    fun qwenFailure_failedNotShown_retriedOnTheNextPass_atMostThreeTimes() = runTest {
        setUp("a s=0.9 l=2", "b s=0.8 l=2")
        qwen.failOnCall = 2
        gate.value = true
        val rt = started()
        assertEquals(AnalysisStatus.FAILED, storage.inner.status("now")) // never DONE → RelatedStore shows nothing
        assertEquals(1, qwenCloses) // closed after the failure too

        qwen.failOnCall = null
        rt.onRecordsChanged() // next save / app start
        advanceUntilIdle()
        assertEquals(AnalysisStatus.DONE, storage.inner.status("now"))
        assertEquals(1, storage.inner.analyses.getValue("now").attempts)
        assertEquals(listOf("c01", "c02"), storage.inner.judgments("now").filter { it.label == 2 }.sortedByDescending { it.similarity }.map { it.candidateId })
    }

    @Test
    fun repeatedRuntimeFailuresStopAfterMaxAttempts() = runTest {
        setUp("a s=0.9 l=2")
        failQwenLoad = true
        gate.value = true
        val rt = started() // attempt 1
        repeat(5) { rt.onRecordsChanged(); advanceUntilIdle() }
        val row = storage.inner.analyses.getValue("now")
        assertEquals(AnalysisStatus.FAILED, row.status)
        assertEquals(LocalRelatedRuntime.MAX_ATTEMPTS, row.attempts)
        assertEquals(e5Loads, e5Closes)
    }

    @Test
    fun invalidOutputIsAFailedPair_theRestContinues() = runTest {
        setUp("a s=0.9 l=2", "b s=0.8 l=2")
        qwen.answer = { _, past -> if (past.startsWith("a")) JudgeResult.Invalid("malformed JSON") else JudgeResult.Label(2) }
        gate.value = true
        started()
        assertEquals(AnalysisStatus.DONE, storage.inner.status("now"))
        val byId = storage.inner.judgments("now").associateBy { it.candidateId }
        assertEquals(JudgmentStatus.FAILED, byId.getValue("c01").status)
        assertEquals(2, byId.getValue("c02").label)
    }

    @Test
    fun qwenInferenceIsNeverConcurrent() = runTest {
        setUp("a s=0.9 l=2", "b s=0.8 l=2", "c s=0.7 l=2")
        storage.inner.add("now2", "두 번째", 101)
        storage.inner.enqueue("now2")
        judgeDelayMs = 100
        val rt = started() // gate closed: nothing ran yet
        gate.value = true  // the collector starts a pass…
        val first = async { rt.drain() } // …while two more passes are requested at the same time
        val second = async { rt.drain() }
        rt.onRecordsChanged()
        advanceUntilIdle()
        first.await(); second.await()
        assertEquals(1, qwenLoads) // the passes ran one after another; later ones found everything cached / done
        assertEquals(1, maxActiveJudgments)
        assertEquals(AnalysisStatus.DONE, storage.inner.status("now"))
        assertEquals(AnalysisStatus.DONE, storage.inner.status("now2"))
    }

    @Test
    fun gateClosingStopsBeforeTheNextTarget_andProcessDeathResumes() = runTest {
        setUp("a s=0.9 l=2")
        storage.inner.analyses["now"] = storage.inner.analyses.getValue("now").copy(status = AnalysisStatus.RUNNING) // killed mid-run
        val rt = started()
        assertEquals(AnalysisStatus.PENDING, storage.inner.status("now")) // start(): RUNNING → PENDING
        gate.value = true
        advanceUntilIdle()
        assertEquals(AnalysisStatus.DONE, storage.inner.status("now"))

        storage.inner.add("later", "나중", 300)
        storage.inner.enqueue("later")
        gate.value = false // 관련된 생각 OFF / models deleted
        advanceUntilIdle()
        assertTrue(rt.drain().outcomes.isEmpty())
        assertEquals(AnalysisStatus.PENDING, storage.inner.status("later"))
    }

    @Test
    fun deletedTarget_isSkipped() = runTest {
        setUp("a s=0.9 l=2")
        storage.inner.delete("now")
        gate.value = true
        val rt = started()
        assertEquals(0, qwenLoads)
        assertNotEquals(null, rt.activePipelineVersion)
        assertTrue(rt.drain().outcomes.none { it is AnalysisOutcome.Done })
    }
}
