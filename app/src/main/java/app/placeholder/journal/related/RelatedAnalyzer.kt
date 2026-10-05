package app.placeholder.journal.related

import app.placeholder.journal.data.db.AnalysisStatus
import app.placeholder.journal.data.db.JudgmentStatus
import app.placeholder.journal.data.db.RecordEmbeddingEntity
import app.placeholder.journal.data.db.RecordTextRow
import app.placeholder.journal.data.db.RelatedJudgmentEntity
import kotlinx.coroutines.CancellationException

/**
 * Analyses ONE target record end to end (docs/m6-related-design.md §2–4), on whatever thread calls it.
 * M6-3: called explicitly (tests). M6-9: a WorkManager worker calls [runNext] in a loop.
 *
 *   PENDING → RUNNING → embeddings (cache) → earlier records → cosine Top 30 → judge each pair (cache)
 *   → judgments saved one by one → DONE (stale / unused pairs pruned) → `RelatedStore` shows label 2, ≤ 5
 *
 * Failure policy (simplest boundary, M5 + design §4):
 * - [JudgeResult.Invalid] or a label outside 0..2 → that pair is cached as FAILED (never shown, not retried for this
 *   pipeline version); the other candidates continue.
 * - A candidate deleted during the run is skipped.
 * - Any exception from the embedder / judge / storage = the runtime cannot work right now → the analysis becomes
 *   FAILED (attempts + 1) and everything computed so far stays cached for the retry.
 * - Cancellation propagates; the row stays RUNNING and `resetRunningToPending` puts it back in the queue.
 * - The target edited / re-queued meanwhile → `complete` refuses (PENDING wins) and the outcome is [AnalysisOutcome.Superseded].
 */
class RelatedAnalyzer(
    private val storage: RelatedAnalysisStorage,
    private val embedder: TextEmbedder,
    private val judge: RelatedValueJudge,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val pipelineVersion: String = RelatedPipeline.version(embedder, judge)

    /** Analyses the oldest PENDING record, or returns null when the queue is empty. */
    suspend fun runNext(): AnalysisOutcome? = storage.nextPendingId()?.let { analyze(it) }

    suspend fun analyze(recordId: String): AnalysisOutcome {
        val row = storage.analysis(recordId) ?: return AnalysisOutcome.Skipped(recordId, "not queued")
        if (row.status != AnalysisStatus.PENDING) return AnalysisOutcome.Skipped(recordId, "status ${row.status}")
        val target = storage.record(recordId) ?: return AnalysisOutcome.Skipped(recordId, "record deleted")
        val targetHash = RelatedText.hash(target.text)
        if (!storage.start(recordId, pipelineVersion, targetHash, now())) return AnalysisOutcome.Skipped(recordId, "not pending")

        val stats = AnalysisStats()
        return try {
            val targetVector = vectorFor(target, targetHash, stats)
            val earlier = storage.recordsBefore(target).filter { it.id != target.id && it.createdAt < target.createdAt }
            val hashes = earlier.associate { it.id to RelatedText.hash(it.text) }
            val texts = earlier.associate { it.id to it.text }
            val candidates = rankCandidates(targetVector, earlier.map { it.id to vectorFor(it, hashes.getValue(it.id), stats) })

            val cached = storage.judgments(recordId)
                .filter { it.pipelineVersion == pipelineVersion && it.targetHash == targetHash }
                .associateBy { it.candidateId }
            val judged = ArrayList<Pair<RelatedCandidate, Int?>>(candidates.size)
            for (c in candidates) {
                val candidateHash = hashes.getValue(c.recordId)
                val hit = cached[c.recordId]?.takeIf { it.candidateHash == candidateHash }
                if (hit != null) {
                    stats.judgeHits++
                    judged += c to hit.label.takeIf { hit.status == JudgmentStatus.OK }
                    continue
                }
                if (storage.record(recordId) == null) return AnalysisOutcome.Skipped(recordId, "record deleted")
                if (storage.record(c.recordId) == null) continue // candidate deleted meanwhile: skip it
                stats.judgeCalls++
                val label = when (val r = judge.judge(target.text, texts.getValue(c.recordId))) {
                    is JudgeResult.Label -> r.value.takeIf(RelatedPolicy::isValidLabel)
                    is JudgeResult.Invalid -> null
                }
                if (label == null) stats.failedPairs++
                storage.saveJudgment(
                    RelatedJudgmentEntity(
                        targetId = recordId,
                        candidateId = c.recordId,
                        pipelineVersion = pipelineVersion,
                        targetHash = targetHash,
                        candidateHash = candidateHash,
                        similarity = c.similarity,
                        label = label,
                        status = if (label == null) JudgmentStatus.FAILED else JudgmentStatus.OK,
                        createdAt = now(),
                    ),
                )
                judged += c to label
            }

            val usedIds = judged.map { it.first.recordId }.toSet()
            if (!storage.complete(recordId, pipelineVersion, targetHash, usedIds, now())) {
                AnalysisOutcome.Superseded(recordId, stats)
            } else {
                AnalysisOutcome.Done(recordId, selectWorthShowing(judged), stats)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            storage.fail(recordId, "${e::class.simpleName}: ${e.message}", now())
            AnalysisOutcome.Failed(recordId, e, stats)
        }
    }

    /** Embedding cache: reuse when made for the current text by this embedder; otherwise embed and store. */
    private suspend fun vectorFor(record: RecordTextRow, textHash: String, stats: AnalysisStats): FloatArray {
        val stored = storage.embedding(record.id)
        if (stored != null && stored.modelId == embedder.modelId && stored.textHash == textHash && stored.vector.size == stored.dim * 4) {
            stats.embedHits++
            return EmbeddingCodec.decode(stored.vector, stored.dim)
        }
        stats.embedCalls++
        val vector = embedder.embed(record.text)
        storage.saveEmbedding(
            RecordEmbeddingEntity(record.id, embedder.modelId, textHash, vector.size, EmbeddingCodec.encode(vector), now()),
        )
        return vector
    }
}

/** Counters for one run (tests / logs). */
data class AnalysisStats(
    var embedCalls: Int = 0,
    var embedHits: Int = 0,
    var judgeCalls: Int = 0,
    var judgeHits: Int = 0,
    var failedPairs: Int = 0,
)

sealed interface AnalysisOutcome {
    val recordId: String

    /** DONE. [resultIds] = what `RelatedStore` will show (label 2, similarity DESC, ≤ 5; may be empty). */
    data class Done(override val recordId: String, val resultIds: List<String>, val stats: AnalysisStats) : AnalysisOutcome

    /** FAILED (runtime error); cached work is kept. */
    data class Failed(override val recordId: String, val error: Throwable, val stats: AnalysisStats) : AnalysisOutcome

    /** Finished, but the target was edited / re-queued meanwhile: nothing shown, the new PENDING run will redo it. */
    data class Superseded(override val recordId: String, val stats: AnalysisStats) : AnalysisOutcome

    /** Nothing to do (not queued, not PENDING, or the record is gone). */
    data class Skipped(override val recordId: String, val reason: String) : AnalysisOutcome
}
