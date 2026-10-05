package app.placeholder.journal.related

import androidx.room.withTransaction
import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.data.db.RecordEmbeddingEntity
import app.placeholder.journal.data.db.RecordTextRow
import app.placeholder.journal.data.db.RelatedAnalysisEntity
import app.placeholder.journal.data.db.RelatedJudgmentEntity

/**
 * What `RelatedAnalyzer` needs from storage — kept small so the pipeline is unit-tested on the JVM with a map-backed
 * fake; [RoomRelatedAnalysisStorage] is the app implementation (instrumentation-tested).
 */
interface RelatedAnalysisStorage {
    /** Oldest PENDING analysis (FIFO), or null. */
    suspend fun nextPendingId(): String?
    suspend fun analysis(recordId: String): RelatedAnalysisEntity?
    suspend fun record(recordId: String): RecordTextRow?
    /** Records written strictly before [target] (not the target). */
    suspend fun recordsBefore(target: RecordTextRow): List<RecordTextRow>
    suspend fun embedding(recordId: String): RecordEmbeddingEntity?
    suspend fun saveEmbedding(embedding: RecordEmbeddingEntity)
    suspend fun judgments(targetId: String): List<RelatedJudgmentEntity>
    suspend fun saveJudgment(judgment: RelatedJudgmentEntity)
    /** PENDING → RUNNING. False when it is not PENDING. */
    suspend fun start(recordId: String, pipelineVersion: String, textHash: String, now: Long): Boolean
    /**
     * RUNNING → DONE if still current, and drop this target's cached pairs outside [usedCandidateIds] (or of another
     * pipeline / target text) — one transaction. False when the record was edited / re-queued meanwhile.
     */
    suspend fun complete(recordId: String, pipelineVersion: String, textHash: String, usedCandidateIds: Set<String>, now: Long): Boolean
    /** RUNNING → FAILED (attempts + 1). Cached embeddings / judgments are kept for the retry. */
    suspend fun fail(recordId: String, error: String, now: Long): Boolean
}

class RoomRelatedAnalysisStorage(private val db: AppDatabase) : RelatedAnalysisStorage {
    private val dao get() = db.relatedDao()

    override suspend fun nextPendingId(): String? = dao.nextPending()?.recordId
    override suspend fun analysis(recordId: String): RelatedAnalysisEntity? = dao.analysis(recordId)
    override suspend fun record(recordId: String): RecordTextRow? = dao.recordText(recordId)
    override suspend fun recordsBefore(target: RecordTextRow): List<RecordTextRow> = dao.recordsBefore(target.createdAt, target.id)
    override suspend fun embedding(recordId: String): RecordEmbeddingEntity? = dao.embedding(recordId)
    override suspend fun saveEmbedding(embedding: RecordEmbeddingEntity) = dao.upsertEmbedding(embedding)
    override suspend fun judgments(targetId: String): List<RelatedJudgmentEntity> = dao.judgmentsFor(targetId)
    override suspend fun saveJudgment(judgment: RelatedJudgmentEntity) = dao.upsertJudgment(judgment)

    override suspend fun start(recordId: String, pipelineVersion: String, textHash: String, now: Long): Boolean =
        dao.markRunning(recordId, pipelineVersion, textHash, now) == 1

    override suspend fun complete(
        recordId: String, pipelineVersion: String, textHash: String, usedCandidateIds: Set<String>, now: Long,
    ): Boolean = db.withTransaction {
        val done = dao.markDone(recordId, pipelineVersion, textHash, now) == 1
        if (done) dao.pruneJudgments(recordId, pipelineVersion, textHash, usedCandidateIds.toList())
        done
    }

    override suspend fun fail(recordId: String, error: String, now: Long): Boolean =
        dao.markFailed(recordId, error.take(500), now) == 1
}
