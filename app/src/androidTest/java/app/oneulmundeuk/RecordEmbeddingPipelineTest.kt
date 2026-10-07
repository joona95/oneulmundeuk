package app.oneulmundeuk

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.related.E5RecordEmbeddingRuntime
import app.oneulmundeuk.related.EmbedderLoader
import app.oneulmundeuk.related.EmbeddingCodec
import app.oneulmundeuk.related.LoadedEmbedder
import app.oneulmundeuk.related.RelatedInvalidator
import app.oneulmundeuk.related.RelatedText
import app.oneulmundeuk.related.RoomRecordEmbeddingStorage
import app.oneulmundeuk.related.TextEmbedder
import app.oneulmundeuk.related.e5.E5Embedder
import app.oneulmundeuk.related.e5.E5Purpose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M6-9 step 1 on real Room (in-memory): save → `RelatedInvalidator` queue → `E5RecordEmbeddingRuntime` → `record_embedding`.
 * The model is a fake behind [EmbedderLoader] (real e5 is checked by E5DeviceCheckTest); everything else is the app's.
 */
@RunWith(AndroidJUnit4::class)
class RecordEmbeddingPipelineTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
    private lateinit var runtime: E5RecordEmbeddingRuntime
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = MutableStateFlow(false)
    private val space = E5Embedder.modelIdFor(E5Purpose.RELATED)
    @Volatile private var failEmbedding = false
    @Volatile private var loads = 0

    private val model = object : TextEmbedder {
        override val modelId = space
        override suspend fun embed(text: String): FloatArray {
            check(!failEmbedding) { "inference failed" }
            return floatArrayOf(text.length.toFloat(), 1f)
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.ENFORCE_FOREIGN_KEYS)
            .build()
        runtime = E5RecordEmbeddingRuntime(
            RoomRecordEmbeddingStorage { db }, gate, prepare = {}, relatedModelId = space,
            loader = EmbedderLoader { loads++; LoadedEmbedder(model) {} }, scope = scope,
        )
        repo = RecordRepository(
            db,
            changeListener = RelatedInvalidator(db, analysisEnabled = { runtime.analysisEnabled }, afterChange = { runtime.onRecordsChanged() }),
        )
        runtime.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun awaitGate(open: Boolean) = withTimeout(5_000) { while (runtime.analysisEnabled != open) delay(10) }

    private suspend fun awaitEmbedding(id: String) = withTimeout(5_000) {
        var row = db.relatedDao().embedding(id, space)
        while (row == null) { delay(20); row = db.relatedDao().embedding(id, space) }
        row!!
    }

    @Test
    fun gateClosed_savesRecordWithoutQueueOrInference() = runBlocking {
        val id = repo.create("모델 없음", null, null)
        delay(200)
        assertNotNull(repo.getRecord(id))
        assertNull(db.relatedDao().analysis(id))
        assertNull(db.relatedDao().embedding(id, space))
        assertEquals(0, loads)
    }

    @Test
    fun gateOpen_savedRecordGetsItsRelatedEmbedding_editReplacesIt() = runBlocking {
        gate.value = true
        awaitGate(true)
        val id = repo.create("오늘 산책을 했다", null, null)
        val row = awaitEmbedding(id)
        assertEquals(RelatedText.hash("오늘 산책을 했다"), row.textHash)
        assertArrayEquals(floatArrayOf(9f, 1f), EmbeddingCodec.decode(row.vector, row.dim), 0f)
        assertEquals(AnalysisStatus.PENDING, db.relatedDao().analysis(id)!!.status) // judge step not here

        repo.update(id, "오늘은 쉬었다", null, null)
        val edited = withTimeout(5_000) {
            var r = db.relatedDao().embedding(id, space)
            while (r?.textHash != RelatedText.hash("오늘은 쉬었다")) { delay(20); r = db.relatedDao().embedding(id, space) }
            r!!
        }
        assertArrayEquals(floatArrayOf(7f, 1f), EmbeddingCodec.decode(edited.vector, edited.dim), 0f)
    }

    @Test
    fun embeddingFailure_neverFailsTheSave_andIsRetriedLater() = runBlocking {
        gate.value = true
        awaitGate(true)
        failEmbedding = true
        val id = repo.create("실패해도 저장", null, null)
        delay(300)
        assertEquals("실패해도 저장", repo.getRecord(id)!!.text) // saved
        assertNull(db.relatedDao().embedding(id, space))
        assertEquals(AnalysisStatus.PENDING, db.relatedDao().analysis(id)!!.status) // still queued, not FAILED

        failEmbedding = false
        assertNull(runtime.embedPending().error) // next pass (next change / app start) succeeds
        assertNotNull(db.relatedDao().embedding(id, space))
    }
}
