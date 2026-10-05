package app.placeholder.journal.related

import androidx.room.withTransaction
import app.placeholder.journal.data.db.AnalysisStatus
import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.data.db.RelatedAnalysisEntity

/**
 * The persistence side of create / edit / delete (docs/m6-related-design.md §3). Runs after the record write has
 * committed (via `RecordRepository`), in one transaction, never runs a model.
 *
 * - create: [analysisEnabled] → the record's analysis is queued (PENDING). Off → nothing (no backlog builds up).
 * - edit, text unchanged (emotion / category only, or whitespace the normalization ignores): nothing.
 * - edit, text changed: own embedding deleted; own judgments (as target) deleted; own analysis → PENDING
 *   (created when enabled, only re-queued when it already exists and the feature is off); judgments where this
 *   record is the candidate deleted and those targets' analyses → PENDING. Other pairs stay cached.
 * - delete: FK CASCADE removes its embedding, analysis and every judgment it is part of. Other records keep the
 *   remaining label 2; no re-analysis is queued to refill the gap.
 *
 * The app wires [analysisEnabled] / [afterChange] from its `RelatedRuntime`: release = off (no model yet, nothing
 * queued, nothing runs); debug = the fake runtime behind a flag file (M6-4).
 */
class RelatedInvalidator(
    private val db: AppDatabase,
    private val analysisEnabled: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    /** Called after each committed change (outside the transaction), e.g. to let a runtime drain the queue. */
    private val afterChange: () -> Unit = {},
) : RecordChangeListener {
    private val dao get() = db.relatedDao()

    override suspend fun onRecordCreated(recordId: String) {
        if (!analysisEnabled()) return
        db.withTransaction {
            val record = db.recordDao().get(recordId) ?: return@withTransaction
            enqueue(recordId, RelatedText.hash(record.text))
        }
        afterChange()
    }

    override suspend fun onRecordUpdated(recordId: String, textChanged: Boolean) {
        if (!textChanged) return
        db.withTransaction {
            val record = db.recordDao().get(recordId) ?: return@withTransaction
            // Only caches made for another text version are touched, so an edit that normalization ignores
            // (e.g. leading whitespace) changes nothing.
            val hash = RelatedText.hash(record.text)
            dao.deleteStaleEmbedding(recordId, hash)
            dao.deleteStaleTargetJudgments(recordId, hash)
            val affected = dao.targetsWithStaleCandidate(recordId, hash)
            dao.deleteStaleCandidateJudgments(recordId, hash)

            val previous = dao.analysis(recordId)
            if (previous?.textHash != hash && (previous != null || analysisEnabled())) enqueue(recordId, hash)
            if (affected.isNotEmpty()) dao.requeue(affected, now())
        }
        afterChange()
    }

    override suspend fun onRecordDeleted(recordId: String) {
        // FK ON DELETE CASCADE already removed everything that referenced the record (records.delete committed first).
    }

    private suspend fun enqueue(recordId: String, textHash: String) {
        val t = now()
        val existing = dao.analysis(recordId)
        dao.upsertAnalysis(
            RelatedAnalysisEntity(
                recordId = recordId,
                status = AnalysisStatus.PENDING,
                pipelineVersion = null,
                textHash = textHash,
                attempts = 0,
                error = null,
                queuedAt = if (existing?.status == AnalysisStatus.PENDING) existing.queuedAt else t,
                updatedAt = t,
                completedAt = existing?.completedAt,
                seenAt = null,
            ),
        )
    }
}
