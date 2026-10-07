package app.oneulmundeuk.related

import androidx.room.withTransaction
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.RecordEmbeddingEntity
import app.oneulmundeuk.data.db.RecordTextRow
import app.oneulmundeuk.data.db.RelatedAnalysisEntity
import app.oneulmundeuk.data.db.RelatedJudgmentEntity

/**
 * What `RelatedAnalyzer` needs from storage — kept small so the pipeline is unit-tested on the JVM with a map-backed
 * fake; [RoomRelatedAnalysisStorage] is the app implementation (instrumentation-tested).
 */
interface RelatedAnalysisStorage : EmbeddingStore {
    /** Oldest PENDING analysis (FIFO), or null. */
    suspend fun nextPendingId(): String?
    suspend fun analysis(recordId: String): RelatedAnalysisEntity?
    suspend fun record(recordId: String): RecordTextRow?
    /** Records written strictly before [target] (not the target). */
    suspend fun recordsBefore(target: RecordTextRow): List<RecordTextRow>
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

open class RoomRelatedAnalysisStorage(private val database: () -> AppDatabase) : RelatedAnalysisStorage {
    constructor(db: AppDatabase) : this({ db })

    /** Resolved on first use (the runtime is created at app start, the database lazily). */
    protected val db: AppDatabase get() = database()
    private val dao get() = db.relatedDao()

    override suspend fun nextPendingId(): String? = dao.nextPending()?.recordId
    override suspend fun analysis(recordId: String): RelatedAnalysisEntity? = dao.analysis(recordId)
    override suspend fun record(recordId: String): RecordTextRow? = dao.recordText(recordId)
    override suspend fun recordsBefore(target: RecordTextRow): List<RecordTextRow> = dao.recordsBefore(target.createdAt, target.id)
    override suspend fun embedding(recordId: String, modelId: String): RecordEmbeddingEntity? = dao.embedding(recordId, modelId)
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
