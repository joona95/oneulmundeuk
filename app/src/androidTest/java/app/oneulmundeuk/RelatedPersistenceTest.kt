package app.oneulmundeuk

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.JudgmentStatus
import app.oneulmundeuk.data.db.RecordEmbeddingEntity
import app.oneulmundeuk.data.db.RelatedAnalysisEntity
import app.oneulmundeuk.data.db.RelatedJudgmentEntity
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.related.RelatedInvalidator
import app.oneulmundeuk.related.RelatedRepository
import app.oneulmundeuk.related.RelatedStore
import app.oneulmundeuk.related.RelatedText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M6-2 persistence on a real SQLite (device / emulator): FK CASCADE, result query, invalidation, queue state.
 * Analyses / judgments are written directly here — M6-3 will be the real writer.
 */
@RunWith(AndroidJUnit4::class)
class RelatedPersistenceTest {
    private lateinit var db: AppDatabase
    private var clock = 1_000L
    private var enabled = true
    private lateinit var repo: RecordRepository
    private val dao get() = db.relatedDao()
    private val store get() = RelatedStore(dao, pv)
    private val pv = "pipeline-test"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.ENFORCE_FOREIGN_KEYS)
            .allowMainThreadQueries()
            .build()
        repo = RecordRepository(db, now = { clock++ }, changeListener = RelatedInvalidator(db, { enabled }, now = { clock++ }))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun text(id: String) = repo.getRecord(id)!!.text
    private suspend fun hash(id: String) = RelatedText.hash(text(id))

    private suspend fun done(target: String) = dao.upsertAnalysis(
        RelatedAnalysisEntity(target, AnalysisStatus.DONE, pv, hash(target), 0, null, clock, clock, clock, null),
    )

    private suspend fun judge(target: String, candidate: String, sim: Float, label: Int?, status: String = JudgmentStatus.OK) =
        dao.upsertJudgment(RelatedJudgmentEntity(target, candidate, pv, hash(target), hash(candidate), sim, label, status, clock))

    private suspend fun embed(id: String) =
        dao.upsertEmbedding(RecordEmbeddingEntity(id, "e5-test", hash(id), 2, ByteArray(8), clock))

    private suspend fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    // ── FK / CASCADE ──

    @Test
    fun deletingTargetRemovesItsEmbeddingAnalysisAndJudgments() = runTest {
        enabled = false
        val a = repo.create("예전 a", null, null)
        val b = repo.create("예전 b", null, null)
        val t = repo.create("지금", null, null)
        embed(t); embed(a); done(t); judge(t, a, .9f, 2); judge(t, b, .8f, 1)

        repo.delete(t)
        assertNull(dao.embedding(t, "e5-test"))
        assertNull(dao.analysis(t))
        assertEquals(0, count("related_judgment"))
        assertNotNull(dao.embedding(a, "e5-test")) // candidates' own data untouched
        assertNotNull(repo.getRecord(a))
    }

    @Test
    fun deletingCandidateRemovesOnlyItsPairsAndNeverRefills() = runTest {
        enabled = false
        val ids = (1..7).map { repo.create("예전 $it", null, null) }
        val t = repo.create("지금", null, null)
        done(t)
        // label 2: ids[0..5] (6 of them), label 1: ids[6] with the highest similarity
        ids.take(6).forEachIndexed { i, id -> judge(t, id, .9f - i / 100f, 2) }
        judge(t, ids[6], .99f, 1)
        assertEquals(ids.take(5), store.resultIds(t))

        repo.delete(ids[1])
        assertEquals(listOf(ids[0], ids[2], ids[3], ids[4], ids[5]), store.resultIds(t)) // next label 2, never label 1
        repo.delete(ids[0]); repo.delete(ids[2])
        assertEquals(listOf(ids[3], ids[4], ids[5]), store.resultIds(t)) // fewer than 5 — not padded
        assertEquals(AnalysisStatus.DONE, dao.analysis(t)!!.status) // no re-analysis queued
        assertEquals(4, dao.judgmentsFor(t).size) // 3 × label 2 + the label 1 pair
    }

    // ── result query ──

    @Test
    fun resultsAreLabel2OfADoneCurrentAnalysisOnly() = runTest {
        enabled = false
        val (a, b, c, d) = (1..4).map { repo.create("예전 $it", null, null) }
        val t = repo.create("지금", null, null)
        judge(t, a, .5f, 2); judge(t, b, .9f, 0); judge(t, c, .8f, null, JudgmentStatus.FAILED); judge(t, d, .7f, 2)
        assertEquals(emptyList<String>(), store.resultIds(t)) // no analysis row yet

        done(t)
        assertEquals(listOf(d, a), store.resultIds(t))
        assertEquals(emptyList<String>(), RelatedStore(dao, "other-pipeline").resultIds(t))
        assertEquals(listOf(d, a), store.observeResultIds(t).first())

        dao.requeue(listOf(t), clock)
        assertEquals(emptyList<String>(), store.resultIds(t)) // only DONE analyses are shown
    }

    @Test
    fun relatedRecordsFollowStoredOrderAndHideWhenStaleOrGone() = runTest {
        enabled = false
        val (a, b, c) = (1..3).map { repo.create("예전 $it", null, null) }
        val t = repo.create("지금", null, null)
        done(t); judge(t, b, .5f, 2); judge(t, c, .8f, 2); judge(t, a, .95f, 1)
        val related = RelatedRepository(store, db.recordDao())

        val r = related.observeRelated(t).first()!!
        assertEquals(t, r.target.record.id)
        assertEquals(listOf(c, b), r.related.map { it.record.id }) // relevance order, records only, never label 1
        repo.delete(c)
        assertEquals(listOf(b), related.observeRelated(t).first()!!.related.map { it.record.id }) // the rest stays
        assertNull(RelatedRepository(RelatedStore(dao, null), db.recordDao()).observeRelated(t).first()) // no active version
        repo.update(t, "지금 고침", null, null) // stale + re-queued (PENDING) → hidden, nothing old shown
        assertNull(related.observeRelated(t).first())
    }

    // ── invalidation ──

    @Test
    fun createQueuesOnlyWhenEnabled() = runTest {
        enabled = false
        val off = repo.create("꺼져 있을 때", null, null)
        assertNull(dao.analysis(off))
        enabled = true
        val on = repo.create("켜져 있을 때", null, null)
        val row = dao.analysis(on)!!
        assertEquals(AnalysisStatus.PENDING, row.status)
        assertEquals(RelatedText.hash("켜져 있을 때"), row.textHash)
        assertEquals(on, dao.nextPending()!!.recordId)
    }

    @Test
    fun editWithoutTextChangeTouchesNothing() = runTest {
        enabled = false
        val a = repo.create("예전", null, null)
        val t = repo.create("지금", null, null)
        embed(t); done(t); judge(t, a, .9f, 2)
        repo.update(t, "지금", Emotion.CALM, null)      // emotion only
        repo.update(t, "  지금", Emotion.SAD, null)     // leading whitespace: same text version
        assertNotNull(dao.embedding(t, "e5-test"))
        assertEquals(AnalysisStatus.DONE, dao.analysis(t)!!.status)
        assertEquals(listOf(a), store.resultIds(t))
    }

    @Test
    fun editTargetTextInvalidatesItsOwnDataAndQueuesIt() = runTest {
        enabled = true
        val a = repo.create("예전", null, null)
        val t = repo.create("지금", null, null)
        embed(t); embed(a); done(t); judge(t, a, .9f, 2)

        dao.upsertEmbedding(RecordEmbeddingEntity(t, "e5-test|explore", hash(t), 2, ByteArray(8), clock)) // 2nd space
        repo.update(t, "지금은 달라졌다", null, null)
        assertNull(dao.embedding(t, "e5-test"))
        assertNull(dao.embedding(t, "e5-test|explore")) // every space of the old text is dropped
        assertNotNull(dao.embedding(a, "e5-test"))
        assertEquals(emptyList<Any>(), dao.judgmentsFor(t))
        val row = dao.analysis(t)!!
        assertEquals(AnalysisStatus.PENDING, row.status)
        assertEquals(RelatedText.hash("지금은 달라졌다"), row.textHash)
        assertEquals(emptyList<String>(), store.resultIds(t))
    }

    @Test
    fun editCandidateTextDropsOnlyItsPairsAndRequeuesThoseTargets() = runTest {
        enabled = true
        val a = repo.create("예전 a", null, null)
        val b = repo.create("예전 b", null, null)
        val t1 = repo.create("지금 1", null, null)
        val t2 = repo.create("지금 2", null, null)
        val t3 = repo.create("지금 3", null, null)
        listOf(t1, t2, t3).forEach { done(it) }
        judge(t1, a, .9f, 2); judge(t1, b, .8f, 2)
        judge(t2, a, .7f, 0)
        judge(t3, b, .6f, 2)

        repo.update(a, "예전 a를 고쳐 씀", null, null)
        assertEquals(listOf(b), dao.judgmentsFor(t1).map { it.candidateId }) // (t1, b) cached
        assertEquals(emptyList<Any>(), dao.judgmentsFor(t2))
        assertEquals(1, dao.judgmentsFor(t3).size)
        assertEquals(AnalysisStatus.PENDING, dao.analysis(t1)!!.status)
        assertEquals(AnalysisStatus.PENDING, dao.analysis(t2)!!.status)
        assertEquals(AnalysisStatus.DONE, dao.analysis(t3)!!.status) // never used a
        assertEquals(listOf(b), store.resultIds(t3))
        assertEquals(AnalysisStatus.PENDING, dao.analysis(a)!!.status) // a itself (enabled) is queued
    }

    @Test
    fun editWhileDisabledRequeuesOnlyExistingAnalyses() = runTest {
        enabled = false
        val a = repo.create("예전", null, null)
        val t = repo.create("지금", null, null)
        repo.update(a, "예전 고침", null, null)
        assertNull(dao.analysis(a)) // never analysed → no new backlog
        done(t); judge(t, a, .9f, 2)
        repo.update(t, "지금 고침", null, null)
        assertEquals(AnalysisStatus.PENDING, dao.analysis(t)!!.status)
    }

    // ── queue state for M6-3 ──

    @Test
    fun queueIsFifoAndResumable() = runTest {
        enabled = true
        val first = repo.create("하나", null, null)
        val second = repo.create("둘", null, null)
        val h1 = hash(first)
        assertEquals(first, dao.nextPending()!!.recordId)
        assertEquals(1, dao.markRunning(first, pv, h1, clock++))
        assertEquals(0, dao.markRunning(first, pv, h1, clock++)) // already running
        assertEquals(second, dao.nextPending()!!.recordId)
        assertEquals(1, dao.resetRunningToPending(clock++)) // process died while RUNNING
        assertEquals(first, dao.nextPending()!!.recordId)   // original queue position kept
        dao.markRunning(first, pv, h1, clock++)
        assertEquals(0, dao.markDone(first, pv, "other text", clock++)) // stale run cannot finish
        assertEquals(1, dao.markDone(first, pv, h1, clock++))
        assertNotNull(dao.analysis(first)!!.completedAt)
        assertEquals(0, dao.markFailed(second, "not running", clock++)) // PENDING stays PENDING
        dao.markRunning(second, pv, hash(second), clock++)
        assertEquals(1, dao.markFailed(second, "model load failed", clock++))
        assertEquals(1, dao.analysis(second)!!.attempts)
        assertNull(dao.nextPending())
        val queuedAt = dao.analysis(second)!!.queuedAt
        dao.requeue(listOf(second), clock + 100)
        assertEquals(AnalysisStatus.PENDING, dao.analysis(second)!!.status)
        assertTrue(dao.analysis(second)!!.queuedAt > queuedAt)
        assertEquals(1, dao.pendingCount())
    }
}
