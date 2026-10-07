package app.oneulmundeuk

import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.JudgmentStatus
import app.oneulmundeuk.related.AnalysisOutcome
import app.oneulmundeuk.related.EmbeddingCodec
import app.oneulmundeuk.related.JudgeResult
import app.oneulmundeuk.related.RelatedAnalyzer
import app.oneulmundeuk.related.RelatedPipeline
import app.oneulmundeuk.related.RelatedPolicy
import app.oneulmundeuk.related.rankCandidates
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * M6-3 pipeline core on the JVM with deterministic fakes (no ONNX / llama.cpp / Room).
 * Candidate texts carry their wanted similarity and label: "c03 s=0.80 l=2" → cosine 0.80 to the target, judged 2.
 */
class RelatedAnalyzerTest {
    private val storage = FakeAnalysisStorage()

    private fun simOf(text: String): Double? = Regex("s=([0-9.]+)").find(text)?.groupValues?.get(1)?.toDouble()

    private val embedder = FakeEmbedder { text ->
        val s = simOf(text) ?: return@FakeEmbedder floatArrayOf(1f, 0f) // targets point at (1, 0)
        floatArrayOf(s.toFloat(), sqrt(1 - s * s).toFloat())
    }

    private val judge = FakeJudge { _, past ->
        when (val l = Regex("l=(-?\\w+)").find(past)?.groupValues?.get(1)) {
            null -> JudgeResult.Label(0)
            "bad" -> JudgeResult.Invalid("malformed JSON")
            else -> JudgeResult.Label(l.toInt())
        }
    }

    private var clock = 1_000L
    private val analyzer get() = RelatedAnalyzer(storage, embedder, judge, now = { clock++ })

    /** Candidates c01.. written at t = 1, 2, … ; the target "now" afterwards. */
    private fun setUp(vararg cands: Pair<Double, Int?>, targetAt: Long = 500): String {
        cands.forEachIndexed { i, (s, l) -> storage.add("c%02d".format(i + 1), "c%02d s=$s".format(i + 1) + (l?.let { " l=$it" } ?: ""), i + 1L) }
        storage.add("now", "지금 기록", targetAt)
        storage.enqueue("now")
        return "now"
    }

    private suspend fun done(id: String = "now") = analyzer.analyze(id) as AnalysisOutcome.Done

    @Test
    fun onlyEarlierRecordsAreCandidates() = runTest {
        storage.add("old", "old s=0.9 l=2", 1)
        storage.add("same", "same s=0.95 l=2", 10) // same timestamp as the target: not "before"
        storage.add("now", "지금", 10)
        storage.add("later", "later s=0.99 l=2", 20)
        storage.enqueue("now")
        val out = done()
        assertEquals(listOf("old"), out.resultIds)
        assertEquals(listOf("old s=0.9 l=2"), judge.calls.map { it.second })
        assertTrue(embedder.calls.none { it.startsWith("later") || it.startsWith("same") })
    }

    @Test
    fun top30ByCosineOnly() = runTest {
        setUp(*Array(40) { i -> (0.30 + i / 100.0) to 2 })
        val out = done()
        assertEquals(30, judge.calls.size)
        assertTrue(judge.calls.none { it.second.startsWith("c01 ") || it.second.startsWith("c10 ") }) // 10 lowest skipped
        assertEquals(listOf("c40", "c39", "c38", "c37", "c36"), out.resultIds)
    }

    @Test
    fun fewerThan30UsesAll() = runTest {
        setUp(0.5 to 0, 0.6 to 1, 0.7 to 2)
        val out = done()
        assertEquals(3, judge.calls.size)
        assertEquals(listOf("c03"), out.resultIds)
    }

    @Test
    fun label2OnlyBySimilarityAtMostFiveNeverPadded() = runTest {
        setUp(0.95 to 1, 0.90 to 2, 0.85 to 0, 0.80 to 2, 0.75 to 2, 0.70 to 2, 0.65 to 2, 0.60 to 2, 0.55 to 1)
        assertEquals(listOf("c02", "c04", "c05", "c06", "c07"), done().resultIds)
    }

    @Test
    fun noLabel2IsADoneAnalysisWithNoResults() = runTest {
        setUp(0.9 to 1, 0.8 to 0)
        val out = done()
        assertEquals(emptyList<String>(), out.resultIds)
        assertEquals(AnalysisStatus.DONE, storage.status("now"))
    }

    @Test
    fun similarityTiesAreOrderedById() = runTest {
        setUp(0.7 to 2, 0.7 to 2, 0.9 to 2)
        assertEquals(listOf("c03", "c01", "c02"), done().resultIds)
        val ranked = rankCandidates(floatArrayOf(1f, 0f), listOf("b" to floatArrayOf(1f, 1f), "a" to floatArrayOf(1f, 1f), "z" to floatArrayOf(1f, 0f)))
        assertEquals(listOf("z", "a", "b"), ranked.map { it.recordId })
    }

    @Test
    fun embeddingCacheHitAndMiss() = runTest {
        setUp(0.9 to 2, 0.8 to 2)
        done()
        assertEquals(3, embedder.calls.size) // target + 2 candidates
        storage.add("next", "다음 기록", 600)
        storage.enqueue("next")
        embedder.calls.clear()
        val out = analyzer.analyze("next") as AnalysisOutcome.Done
        assertEquals(listOf("다음 기록"), embedder.calls) // only the new target; c01, c02, "now" reused
        assertEquals(3, out.stats.embedHits)

        // stale by text and by model → re-embedded
        storage.edit("c01", "c01 s=0.9 l=2 고침")
        storage.enqueue("next")
        embedder.calls.clear()
        analyzer.analyze("next")
        assertEquals(listOf("c01 s=0.9 l=2 고침"), embedder.calls)
        val other = RelatedAnalyzer(storage, FakeEmbedder("fake-e5-v2", embedder.vectors), judge, now = { clock++ })
        storage.enqueue("next")
        assertEquals(4, (other.analyze("next") as AnalysisOutcome.Done).stats.embedCalls)
    }

    @Test
    fun judgmentCacheHitAndMiss() = runTest {
        setUp(0.9 to 2, 0.8 to 1, 0.7 to 0)
        val first = done()
        assertEquals(3, first.stats.judgeCalls)
        storage.enqueue("now")
        judge.calls.clear(); embedder.calls.clear()
        val again = done()
        assertEquals(0, judge.calls.size)
        assertEquals(0, embedder.calls.size)
        assertEquals(3, again.stats.judgeHits)
        assertEquals(first.resultIds, again.resultIds)
        // a new judge version cannot reuse the pairs
        val v2 = RelatedAnalyzer(storage, embedder, FakeJudge("fake-qwen-v2", judge.answer), now = { clock++ })
        storage.enqueue("now")
        assertEquals(3, (v2.analyze("now") as AnalysisOutcome.Done).stats.judgeCalls)
    }

    @Test
    fun invalidOutputsAreFailedPairsAndTheRestContinues() = runTest {
        setUp(0.9 to null, 0.8 to 2, 0.7 to 7)
        storage.edit("c01", "c01 s=0.9 l=bad")
        val out = done()
        assertEquals(listOf("c02"), out.resultIds)
        assertEquals(2, out.stats.failedPairs) // "bad" output and label 7
        val failed = storage.judgmentRows.values.filter { it.status == JudgmentStatus.FAILED }.map { it.candidateId }.sorted()
        assertEquals(listOf("c01", "c03"), failed)
        assertTrue(storage.judgmentRows.values.filter { it.status == JudgmentStatus.FAILED }.all { it.label == null })
        storage.enqueue("now")
        judge.calls.clear()
        done()
        assertEquals(0, judge.calls.size) // FAILED pairs are not retried for the same pipeline version
    }

    @Test
    fun runtimeFailureKeepsCacheAndResumes() = runTest {
        setUp(0.9 to 2, 0.8 to 2, 0.7 to 2, 0.6 to 2)
        judge.failOnCall = 3
        val failed = analyzer.analyze("now")
        assertTrue(failed is AnalysisOutcome.Failed)
        assertEquals(AnalysisStatus.FAILED, storage.status("now"))
        assertEquals(1, storage.analyses.getValue("now").attempts)
        assertEquals(2, storage.judgments("now").size) // pairs judged before the failure are cached

        judge.failOnCall = null; judge.calls.clear(); embedder.calls.clear()
        storage.enqueue("now") // M6-9: the worker's retry
        val out = done()
        assertEquals(2, judge.calls.size) // only the two missing pairs
        assertEquals(0, embedder.calls.size)
        assertEquals(listOf("c01", "c02", "c03", "c04"), out.resultIds)
    }

    @Test
    fun embedderFailureFailsTheAnalysis() = runTest {
        setUp(0.9 to 2)
        embedder.failOnCall = 1
        assertTrue(analyzer.analyze("now") is AnalysisOutcome.Failed)
        assertEquals(AnalysisStatus.FAILED, storage.status("now"))
        assertEquals(0, judge.calls.size)
    }

    @Test
    fun editDuringRunSupersedesAndNothingIsShown() = runTest {
        setUp(0.9 to 2, 0.8 to 2)
        judge.beforeAnswer = { n -> if (n == 1) { storage.edit("now", "지금 기록 고침"); storage.analyses.remove("now"); storage.enqueue("now") } }
        val out = analyzer.analyze("now")
        assertTrue(out is AnalysisOutcome.Superseded)
        assertEquals(AnalysisStatus.PENDING, storage.status("now")) // the new version waits in the queue
        judge.beforeAnswer = {}
        judge.calls.clear()
        done()
        assertEquals(2, judge.calls.size) // old-text pairs were not reused for the new text
    }

    @Test
    fun finishedRunPrunesPairsItDidNotUse() = runTest {
        setUp(0.9 to 2, 0.8 to 2)
        done()
        storage.delete("c02")
        storage.add("c00", "c00 s=0.5 l=2", 0)
        storage.enqueue("now")
        done()
        assertEquals(setOf("c01", "c00"), storage.judgments("now").map { it.candidateId }.toSet())
    }

    @Test
    fun skipsWhatIsNotPendingOrGone() = runTest {
        setUp(0.9 to 2)
        assertTrue(analyzer.analyze("c01") is AnalysisOutcome.Skipped) // never queued
        done()
        assertTrue(analyzer.analyze("now") is AnalysisOutcome.Skipped) // DONE, not PENDING
        storage.enqueue("now"); storage.delete("now")
        assertTrue(analyzer.analyze("now") is AnalysisOutcome.Skipped)
        assertNull(analyzer.runNext())
    }

    @Test
    fun runNextIsFifo() = runTest {
        storage.add("a", "a", 1); storage.add("b", "b s=0.5 l=2", 2)
        storage.enqueue("b"); storage.enqueue("a")
        assertEquals("b", analyzer.runNext()!!.recordId)
        assertEquals("a", analyzer.runNext()!!.recordId)
        assertNull(analyzer.runNext())
    }

    @Test
    fun codecAndVersion() {
        val v = floatArrayOf(0.25f, -1.5f, 3.0e-7f, 0f)
        val bytes = EmbeddingCodec.encode(v)
        assertEquals(16, bytes.size)
        assertArrayEquals(v, EmbeddingCodec.decode(bytes, 4), 0f)
        assertEquals(0x00.toByte(), bytes[0]); assertEquals(0x3e.toByte(), bytes[3]) // 0.25f little-endian = 00 00 80 3E
        val version = RelatedPipeline.version(FakeEmbedder("e5-x") { floatArrayOf() }, FakeJudge("qwen-y") { _, _ -> JudgeResult.Label(0) })
        assertTrue("e=e5-x" in version && "j=qwen-y" in version && RelatedPipeline.POLICY in version)
    }

    /** M6-7 candidate budget: cached records always count; missing ones are embedded newest first, ties by id. */
    @Test
    fun candidatePoolEmbedsAtMostTheBudgetNewestFirst() = runTest {
        storage.add("old", "old s=0.99 l=2", 1)
        storage.add("b", "b s=0.5 l=2", 5)
        storage.add("a", "a s=0.5 l=2", 5) // same time as b → id order
        storage.add("new", "new s=0.4 l=2", 9)
        storage.add("now", "지금", 10)
        storage.enqueue("now")
        val budgeted = RelatedAnalyzer(storage, embedder, judge, now = { clock++ }, embedBudget = 3)
        val out = budgeted.analyze("now") as AnalysisOutcome.Done
        assertEquals(listOf("지금", "new s=0.4 l=2", "a s=0.5 l=2", "b s=0.5 l=2"), embedder.calls) // "old" over budget
        assertEquals(listOf("a", "b", "new"), out.resultIds) // similarity DESC, tie a < b

        // next analysis: the cached three are free, the budget reaches "old"
        storage.add("next", "다음", 11)
        storage.enqueue("next")
        embedder.calls.clear()
        val next = budgeted.analyze("next") as AnalysisOutcome.Done
        assertEquals(listOf("다음", "old s=0.99 l=2"), embedder.calls)
        assertEquals("old", next.resultIds.first())
        assertEquals(RelatedPolicy.EMBED_BUDGET_PER_ANALYSIS, 100)
        assertTrue("eb100" in RelatedPipeline.POLICY)
    }
}
