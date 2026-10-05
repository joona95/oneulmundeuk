package app.placeholder.journal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.db.AnalysisStatus
import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.related.AnalysisOutcome
import app.placeholder.journal.related.JudgeResult
import app.placeholder.journal.related.RelatedAnalyzer
import app.placeholder.journal.related.RelatedInvalidator
import app.placeholder.journal.related.RelatedStore
import app.placeholder.journal.related.RelatedValueJudge
import app.placeholder.journal.related.RoomRelatedAnalysisStorage
import app.placeholder.journal.related.TextEmbedder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/**
 * M6-3 on real Room: RecordRepository → RelatedInvalidator (queue / invalidation) → RelatedAnalyzer (fake models)
 * → RelatedStore. Texts carry their test similarity / label: "예전 s=0.80 l=2".
 */
@RunWith(AndroidJUnit4::class)
class RelatedPipelineTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
    private var clock = 1_000L

    private class Embedder : TextEmbedder {
        override val modelId = "fake-e5"
        val calls = mutableListOf<String>()
        override suspend fun embed(text: String): FloatArray {
            calls += text
            val s = Regex("s=([0-9.]+)").find(text)?.groupValues?.get(1)?.toDouble() ?: return floatArrayOf(1f, 0f)
            return floatArrayOf(s.toFloat(), sqrt(1 - s * s).toFloat())
        }
    }

    private class Judge : RelatedValueJudge {
        override val modelId = "fake-qwen"
        val calls = mutableListOf<String>()
        var during: suspend () -> Unit = {}
        override suspend fun judge(current: String, past: String): JudgeResult {
            calls += past
            during()
            return JudgeResult.Label(Regex("l=(\\d)").find(past)?.groupValues?.get(1)?.toInt() ?: 0)
        }
    }

    private val embedder = Embedder()
    private val judge = Judge()
    private lateinit var analyzer: RelatedAnalyzer
    private val store get() = RelatedStore(db.relatedDao())
    private val pv get() = analyzer.pipelineVersion

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.ENFORCE_FOREIGN_KEYS)
            .allowMainThreadQueries()
            .build()
        repo = RecordRepository(db, now = { clock++ }, changeListener = RelatedInvalidator(db, { true }, now = { clock++ }))
        analyzer = RelatedAnalyzer(RoomRelatedAnalysisStorage(db), embedder, judge, now = { clock++ })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun drain(): List<AnalysisOutcome> {
        val out = mutableListOf<AnalysisOutcome>()
        while (true) out += analyzer.runNext() ?: break
        return out
    }

    private suspend fun status(id: String) = db.relatedDao().analysis(id)?.status

    @Test
    fun newRecordGoesPendingToDoneAndIsShown() = runTest {
        val a = repo.create("예전 a s=0.90 l=2", null, null)
        val b = repo.create("예전 b s=0.80 l=1", null, null)
        val c = repo.create("예전 c s=0.70 l=2", null, null)
        drain()
        val t = repo.create("지금", null, null)
        assertEquals(AnalysisStatus.PENDING, status(t))
        assertEquals(emptyList<String>(), store.resultIds(t, pv)) // PENDING → nothing shown
        val out = drain().last() as AnalysisOutcome.Done
        assertEquals(listOf(a, c), out.resultIds)
        assertEquals(AnalysisStatus.DONE, status(t))
        assertEquals(listOf(a, c), store.resultIds(t, pv)) // store agrees with the run
        assertTrue(b !in store.resultIds(t, pv))
    }

    @Test
    fun runningIsNotShown() = runTest {
        repo.create("예전 s=0.9 l=2", null, null)
        drain()
        val t = repo.create("지금", null, null)
        var seenWhileRunning: List<String>? = null
        judge.during = { seenWhileRunning = store.resultIds(t, pv); judge.during = {} }
        drain()
        assertEquals(emptyList<String>(), seenWhileRunning)
        assertEquals(1, store.resultIds(t, pv).size)
    }

    @Test
    fun editingTheTargetRecomputesOnlyItsOwnPart() = runTest {
        val ids = (1..4).map { repo.create("예전 $it s=0.${9 - it} l=2", null, null) }
        val t = repo.create("지금", null, null)
        drain()
        embedder.calls.clear(); judge.calls.clear()

        repo.update(t, "지금 고쳐 씀", null, null)
        assertEquals(AnalysisStatus.PENDING, status(t))
        assertEquals(emptyList<String>(), store.resultIds(t, pv))
        drain()
        assertEquals(listOf("지금 고쳐 씀"), embedder.calls) // candidates' embeddings reused
        assertEquals(4, judge.calls.size)                    // every pair has a new target text
        assertEquals(ids, store.resultIds(t, pv))
    }

    @Test
    fun editingACandidateRequeuesAffectedTargetsAndRejudgesOnlyThatPair() = runTest {
        val a = repo.create("예전 a s=0.9 l=2", null, null)
        val b = repo.create("예전 b s=0.8 l=2", null, null)
        val t = repo.create("지금", null, null)
        drain()
        embedder.calls.clear(); judge.calls.clear()

        repo.update(a, "예전 a 고침 s=0.9 l=1", null, null)
        assertEquals(AnalysisStatus.PENDING, status(t))
        assertEquals(emptyList<String>(), store.resultIds(t, pv)) // stale → hidden until the new DONE
        drain()
        assertTrue(judge.calls.all { it.startsWith("예전 a 고침") }) // (t, b) reused
        assertEquals(listOf(b), store.resultIds(t, pv))
    }

    @Test
    fun sameTextEditReusesEverything() = runTest {
        repo.create("예전 s=0.9 l=2", null, null)
        val t = repo.create("지금", null, null)
        drain()
        repo.update(t, "  지금", null, null) // normalization: same version
        assertEquals(AnalysisStatus.DONE, status(t))
        assertEquals(emptyList<AnalysisOutcome>(), drain())
        assertEquals(1, store.resultIds(t, pv).size)
    }

    @Test
    fun deletingACandidateShowsOnlyRemainingLabel2() = runTest {
        val ids = (1..7).map { repo.create("예전 $it s=0.${9 - it}5 l=2", null, null) }
        val t = repo.create("지금", null, null)
        drain()
        assertEquals(ids.take(5), store.resultIds(t, pv))
        repo.delete(ids[0]); repo.delete(ids[1]); repo.delete(ids[2])
        assertEquals(ids.drop(3), store.resultIds(t, pv)) // 4 left, not refilled
        assertEquals(AnalysisStatus.DONE, status(t))
        assertEquals(emptyList<AnalysisOutcome>(), drain())
    }

    @Test
    fun failureKeepsCacheAndRetryResumes() = runTest {
        (1..3).forEach { repo.create("예전 $it s=0.${9 - it} l=2", null, null) }
        drain()
        val t = repo.create("지금", null, null)
        var n = 0
        judge.during = { if (++n == 2) throw IllegalStateException("judge OOM") }
        assertTrue(drain().single() is AnalysisOutcome.Failed)
        assertEquals(AnalysisStatus.FAILED, status(t))
        assertEquals(1, db.relatedDao().judgmentsFor(t).size)
        judge.during = {}; judge.calls.clear()
        db.relatedDao().requeue(listOf(t), clock++) // M6-9 retry
        drain()
        assertEquals(2, judge.calls.size)
        assertEquals(3, store.resultIds(t, pv).size)
    }
}
