package app.oneulmundeuk

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.related.JudgeResult
import app.oneulmundeuk.related.LoadedModel
import app.oneulmundeuk.related.LocalRelatedRuntime
import app.oneulmundeuk.related.ModelLoader
import app.oneulmundeuk.related.RelatedInvalidator
import app.oneulmundeuk.related.RelatedRecords
import app.oneulmundeuk.related.RelatedRepository
import app.oneulmundeuk.related.RelatedStore
import app.oneulmundeuk.related.RelatedValueJudge
import app.oneulmundeuk.related.RoomRelatedRuntimeStorage
import app.oneulmundeuk.related.TextEmbedder
import app.oneulmundeuk.related.e5.E5Embedder
import app.oneulmundeuk.related.e5.E5Purpose
import app.oneulmundeuk.related.qwen.QwenJudge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/**
 * M6-7 end to end on real Room (in-memory): save → `RelatedInvalidator` queue → `LocalRelatedRuntime` (e5 RELATED cache
 * → Top 30 → judge) → DONE → `RelatedRepository.observeRelated` (what the save grace window, Detail `이어지는 기록`
 * and Related Memories read). Models are fakes behind [ModelLoader]; the real ones are checked by E5DeviceCheckTest /
 * QwenDeviceCheckTest. Texts carry the fake answers: "s=0.9" cosine to the target, "l=2" label.
 */
@RunWith(AndroidJUnit4::class)
class LocalRelatedPipelineTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
    private lateinit var runtime: LocalRelatedRuntime
    private lateinit var related: RelatedRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = MutableStateFlow(false)
    private val e5Id = E5Embedder.modelIdFor(E5Purpose.RELATED)
    @Volatile private var failJudge = false
    @Volatile private var judgeLoads = 0
    private var clock = 1_000L

    private val e5 = object : TextEmbedder {
        override val modelId = e5Id
        override suspend fun embed(text: String): FloatArray {
            val s = Regex("s=([0-9.]+)").find(text)?.groupValues?.get(1)?.toFloat() ?: return floatArrayOf(1f, 0f)
            return floatArrayOf(s, sqrt(1 - s * s))
        }
    }
    private val qwen = object : RelatedValueJudge {
        override val modelId = QwenJudge.MODEL_ID
        override suspend fun judge(current: String, past: String): JudgeResult {
            check(!failJudge) { "qwen complete: native error" }
            return JudgeResult.Label(Regex("l=(\\d)").find(past)?.groupValues?.get(1)?.toInt() ?: 0)
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.ENFORCE_FOREIGN_KEYS)
            .build()
        runtime = LocalRelatedRuntime(
            RoomRelatedRuntimeStorage { db }, gate, prepare = {}, embedderModelId = e5Id, judgeModelId = QwenJudge.MODEL_ID,
            embedderLoader = ModelLoader { LoadedModel<TextEmbedder>(e5) {} },
            judgeLoader = ModelLoader { judgeLoads++; LoadedModel<RelatedValueJudge>(qwen) {} },
            scope = scope,
        )
        repo = RecordRepository(
            db,
            now = { clock++ },
            changeListener = RelatedInvalidator(db, analysisEnabled = { runtime.analysisEnabled }, afterChange = { runtime.onRecordsChanged() }),
        )
        related = RelatedRepository(RelatedStore(db.relatedDao(), runtime.activePipelineVersion), db.recordDao())
        runtime.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun openGate() {
        gate.value = true
        withTimeout(5_000) { while (!runtime.analysisEnabled) delay(10) }
    }

    private suspend fun awaitStatus(id: String, status: String) =
        withTimeout(10_000) { while (db.relatedDao().analysis(id)?.status != status) delay(20) }

    private suspend fun shown(id: String): RelatedRecords? = related.observeRelated(id).first()

    @Test
    fun gateClosed_saveSucceeds_noQueueNoInference() = runBlocking {
        repo.create("예전 s=0.9 l=2", null, null)
        val id = repo.create("지금", null, null)
        delay(300)
        assertNotNull(repo.getRecord(id))
        assertNull(db.relatedDao().analysis(id))
        assertEquals(0, judgeLoads)
        assertNull(shown(id))
    }

    @Test
    fun gateOpen_saveIsJudgedAndOnlyLabel2IsShown_inSimilarityOrder() = runBlocking {
        val a = repo.create("관련 s=0.90 l=2", null, null)
        repo.create("애매 s=0.95 l=1", null, null)
        repo.create("무관 s=0.99 l=0", null, null)
        val d = repo.create("덜 관련 s=0.80 l=2", null, null)
        openGate()
        val id = repo.create("지금 쓴 기록", null, null)
        awaitStatus(id, AnalysisStatus.DONE)
        val result = withTimeout(5_000) { related.observeRelated(id).first { it != null } }!!
        assertEquals(id, result.target.record.id)
        assertEquals(listOf(a, d), result.related.map { it.record.id }) // label 0 / 1 never, relevance order kept
        assertNotNull(db.relatedDao().embedding(id, e5Id)) // RELATED cache row of the target
    }

    @Test
    fun qwenFailure_saveSucceeds_nothingPartialShown_retriedOnNextSave() = runBlocking {
        repo.create("관련 s=0.9 l=2", null, null)
        openGate()
        failJudge = true
        val id = repo.create("실패해도 저장", null, null)
        awaitStatus(id, AnalysisStatus.FAILED)
        assertEquals("실패해도 저장", repo.getRecord(id)!!.text)
        assertNull(shown(id))

        failJudge = false
        repo.create("다음 저장", null, null) // next change → next pass re-queues the FAILED analysis
        awaitStatus(id, AnalysisStatus.DONE)
        assertEquals(1, shown(id)!!.related.size)
    }

    @Test
    fun editRequeuesAndHidesUntilDoneAgain_lateResultStillReadableLater() = runBlocking {
        val a = repo.create("관련 s=0.9 l=2", null, null)
        val b = repo.create("다른 관련 s=0.8 l=2", null, null)
        openGate()
        val id = repo.create("지금", null, null)
        awaitStatus(id, AnalysisStatus.DONE)
        assertEquals(listOf(a, b), shown(id)!!.related.map { it.record.id })

        repo.update(b, "이제 무관 s=0.8 l=0", null, null) // candidate text changed → the target is re-judged
        awaitStatus(id, AnalysisStatus.DONE)
        withTimeout(5_000) { while (shown(id)?.related?.map { it.record.id } != listOf(a)) delay(20) }

        // a result that became DONE long after saving is still what Detail reads (no time limit outside the save window)
        delay(200)
        assertEquals(listOf(a), shown(id)!!.related.map { it.record.id })
    }
}
