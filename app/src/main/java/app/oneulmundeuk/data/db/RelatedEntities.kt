package app.oneulmundeuk.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * M6 related-record persistence (docs/m6-related-design.md §3), added by Migration(1, 2).
 * Every table points at records.id with ON DELETE CASCADE: deleting a record removes its embedding, its own
 * analysis, and every judgment where it is the target OR the candidate. Nothing here cascades into records.
 * Text versions are `RelatedText.hash(text)` values; a row whose hash differs from the current text is stale.
 */

/**
 * Embedding of one record in one embedding space. [modelId] = the space: model artifact + how the record text is
 * prepared for it (e5: purpose → prefix, e.g. Related "query: " vs Explore "passage: "), so one record can hold one
 * row per space (v4: primary key record_id + model_id) and a vector is only ever reused for the same space.
 * Valid only while [textHash] matches the current text.
 */
@Entity(
    tableName = "record_embedding",
    primaryKeys = ["record_id", "model_id"],
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["record_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RecordEmbeddingEntity(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "text_hash") val textHash: String,
    val dim: Int,
    /** Little-endian float32 × [dim]. */
    val vector: ByteArray,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    // ByteArray needs content equality for a data class used in tests / diffing.
    override fun equals(other: Any?): Boolean = other is RecordEmbeddingEntity && recordId == other.recordId &&
        modelId == other.modelId && textHash == other.textHash && dim == other.dim && updatedAt == other.updatedAt &&
        vector.contentEquals(other.vector)

    override fun hashCode(): Int = 31 * recordId.hashCode() + modelId.hashCode()
}

/**
 * Durable queue + state of the analysis of one target record (one row per record, at most).
 * PENDING rows are the queue, oldest [queuedAt] first. RUNNING left behind by a killed process goes back to PENDING.
 * [completedAt] is set when an analysis finishes (DONE); results are read only while status is DONE.
 */
@Entity(
    tableName = "related_analysis",
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["record_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["status", "queued_at"])],
)
data class RelatedAnalysisEntity(
    @PrimaryKey @ColumnInfo(name = "record_id") val recordId: String,
    /** [AnalysisStatus] key. */
    val status: String,
    /** Pipeline version the analysis ran / runs with; null until it starts. */
    @ColumnInfo(name = "pipeline_version") val pipelineVersion: String?,
    /** Hash of the target text this analysis is for. */
    @ColumnInfo(name = "text_hash") val textHash: String,
    val attempts: Int,
    val error: String?,
    @ColumnInfo(name = "queued_at") val queuedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    /** When the user saw this result on Home (M6-4). */
    @ColumnInfo(name = "seen_at") val seenAt: Long?,
)

/** Stable status keys stored in `related_analysis.status` (never ordinals). */
object AnalysisStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

/**
 * Pair judgment cache: target ← candidate (an earlier record). Reused while both hashes and [pipelineVersion]
 * match; a failed judgment (status FAILED, label null) is cached too and never shown.
 */
@Entity(
    tableName = "related_judgment",
    primaryKeys = ["target_id", "candidate_id"],
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["target_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["candidate_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("candidate_id")],
)
data class RelatedJudgmentEntity(
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "candidate_id") val candidateId: String,
    @ColumnInfo(name = "pipeline_version") val pipelineVersion: String,
    @ColumnInfo(name = "target_hash") val targetHash: String,
    @ColumnInfo(name = "candidate_hash") val candidateHash: String,
    /** e5 cosine similarity target ↔ candidate (orders the shown results). */
    val similarity: Float,
    /** judge_v1 label 0 / 1 / 2; null when the judgment failed. */
    val label: Int?,
    /** [JudgmentStatus] key. */
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

object JudgmentStatus {
    const val OK = "OK"
    const val FAILED = "FAILED"
}
