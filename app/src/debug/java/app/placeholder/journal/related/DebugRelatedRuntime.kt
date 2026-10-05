package app.placeholder.journal.related

import android.app.Application
import android.util.Log
import app.placeholder.journal.data.db.AnalysisStatus
import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.data.db.RelatedAnalysisEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.sqrt

/*
 * DEBUG BUILD ONLY (src/debug). M6-4 fake related results — delete this file (and the debug factory) when the real
 * runtime lands; nothing in `main` refers to it.
 *
 * Off by default. On = the file `files/debug_related_on` exists when the app process starts:
 *   adb shell run-as app.placeholder.journal touch files/debug_related_on
 *   adb shell am force-stop app.placeholder.journal   (then open the app again)
 * Off: `rm` the same file + force-stop. Results stay in Room but are hidden (the active version becomes null).
 *
 * Fake model latency (DEBUG ONLY — not the product's timing; the save grace window is `RelatedGrace.WINDOW_MS`):
 *   empty flag file → fast UI-test mode (5 ms per judged pair, and the first judged — i.e. most similar — candidate of
 *     every run is always label 2): any record with an earlier record gets a result inside the save grace window.
 *   flag file containing `slow` → 350 ms per pair, plain fake labels only: results miss the window (or are 0),
 *     PENDING / RUNNING stay visible longer, results show up later in Detail.
 *   adb shell "run-as app.placeholder.journal sh -c 'echo slow > files/debug_related_on'"
 *
 * When on: the latest 10 records are queued once (like the real first activation), new / edited records are queued
 * by `RelatedInvalidator`, and the queue is drained in the background through the REAL `RelatedAnalyzer` +
 * `RoomRelatedAnalysisStorage` → `RelatedStore`. Only the two models are fakes: deterministic, no network, no files.
 */
class DebugRelatedRuntime(
    app: Application,
    private val database: () -> AppDatabase,
) : RelatedRuntime {
    private val flag = File(app.filesDir, FLAG_FILE)
    private val on = flag.exists()
    private val slow = on && runCatching { flag.readText().contains("slow") }.getOrDefault(false)
    private val embedder = DebugFakeEmbedder()
    private val judge = DebugFakeJudge(pairDelayMs = if (slow) SLOW_PAIR_MS else FAST_PAIR_MS, topCandidateIsLabel2 = !slow)
    private val analyzer by lazy { RelatedAnalyzer(RoomRelatedAnalysisStorage(database()), embedder, judge) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    override val activePipelineVersion: String? = if (on) RelatedPipeline.version(embedder, judge) else null
    override val analysisEnabled: Boolean get() = on

    override fun start() {
        if (!on) return
        Log.i(TAG, "debug fake related ON (${if (slow) "slow" else "fast"}, $activePipelineVersion)")
        scope.launch {
            backfillLatest()
            drain()
        }
    }

    override fun onRecordsChanged() {
        if (on) scope.launch { drain() }
    }

    /** First activation: queue the latest [BACKFILL] records that have no current analysis (newest analysed first). */
    private suspend fun backfillLatest() = mutex.withLock {
        val db = database()
        val dao = db.relatedDao()
        val now = System.currentTimeMillis()
        dao.resetRunningToPending(now)
        db.recordDao().observeAll().first().take(BACKFILL).forEachIndexed { i, item ->
            val hash = RelatedText.hash(item.record.text)
            val row = dao.analysis(item.record.id)
            val current = row != null && (row.status != AnalysisStatus.DONE || (row.pipelineVersion == activePipelineVersion && row.textHash == hash))
            if (current) return@forEachIndexed // PENDING / RUNNING / FAILED (M6-9) / up-to-date DONE stay as they are
            dao.upsertAnalysis(
                RelatedAnalysisEntity(
                    recordId = item.record.id, status = AnalysisStatus.PENDING, pipelineVersion = null, textHash = hash,
                    attempts = 0, error = null, queuedAt = now + i, updatedAt = now, completedAt = null, seenAt = null,
                ),
            )
        }
    }

    private suspend fun drain() = mutex.withLock {
        repeat(MAX_RUNS_PER_DRAIN) {
            judge.newRun()
            val outcome = runCatching { analyzer.runNext() }.getOrElse { Log.w(TAG, "run failed", it); return@withLock } ?: return@withLock
            Log.i(TAG, outcome.toString().take(200))
        }
    }

    companion object {
        const val FLAG_FILE = "debug_related_on"
        private const val TAG = "DebugRelated"
        private const val BACKFILL = 10
        private const val MAX_RUNS_PER_DRAIN = 200
        private const val FAST_PAIR_MS = 5L
        private const val SLOW_PAIR_MS = 350L
    }
}

/** Fake e5: hashed character bigrams (whitespace ignored), L2-normalised. Similar wording → higher cosine. */
class DebugFakeEmbedder : TextEmbedder {
    override val modelId = "debug-fake-bigram64"

    override suspend fun embed(text: String): FloatArray {
        val chars = text.filterNot(Char::isWhitespace)
        val v = FloatArray(DIM)
        v[0] = 0.1f // never a zero vector
        val grams = if (chars.length < 2) listOf(chars) else chars.windowed(2)
        grams.forEach { v[1 + Math.floorMod(it.hashCode(), DIM - 1)] += 1f }
        val norm = sqrt(v.fold(0f) { acc, x -> acc + x * x })
        return FloatArray(DIM) { v[it] / norm }
    }

    private companion object { const val DIM = 64 }
}

/**
 * Fake judge_v1: the label is a fixed function of the two text versions (≈50% label 2, 20% label 1, 30% label 0),
 * so the same records always give the same results. [pairDelayMs] imitates model latency (debug knob only).
 *
 * [topCandidateIsLabel2] (fast UI-test mode only): the first pair judged after [newRun] is label 2. `RelatedAnalyzer`
 * judges candidates in relevance order (similarity DESC) and skips cached pairs, so this is the most similar
 * not-yet-judged candidate — normally relevance #1 — and every run with a candidate yields ≥ 1 result.
 * It changes what the judge answers, so it is part of [modelId]: fast and slow are different fake pipeline versions
 * (switching mode re-analyses the latest 10 at start and hides the other mode's results). The delay is not part of it.
 * The real pipeline policy (label 2 only, ≤ 5, never padded) is untouched — this only shapes the fake model's answers.
 */
class DebugFakeJudge(
    private val pairDelayMs: Long,
    private val topCandidateIsLabel2: Boolean,
) : RelatedValueJudge {
    override val modelId = if (topCandidateIsLabel2) "debug-fake-pairhash-top2" else "debug-fake-pairhash"

    @Volatile private var firstOfRun = false

    /** Called by the debug runtime before each analysis (runs are serialised by its mutex). */
    fun newRun() {
        firstOfRun = true
    }

    override suspend fun judge(current: String, past: String): JudgeResult {
        delay(pairDelayMs)
        if (topCandidateIsLabel2 && firstOfRun) {
            firstOfRun = false
            return JudgeResult.Label(2)
        }
        firstOfRun = false
        val h = Math.floorMod((RelatedText.hash(current) + "|" + RelatedText.hash(past)).hashCode(), 10)
        return JudgeResult.Label(
            when {
                h < 5 -> 2
                h < 7 -> 1
                else -> 0
            },
        )
    }
}
