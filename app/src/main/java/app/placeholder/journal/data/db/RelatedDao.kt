package app.placeholder.journal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One shown-result candidate row before the text-version check (see `RelatedStore`). */
data class RelatedResultRow(
    val candidateId: String,
    val similarity: Float,
    val targetHash: String,
    val candidateHash: String,
    val analysisHash: String,
    val targetText: String,
    val candidateText: String,
)

/**
 * Persistence for M6 related records. Plain reads / writes only — no analysis logic (M6-3).
 * Invalidation sequences live in `RelatedInvalidator`; result rules in `RelatedStore`.
 */
@Dao
interface RelatedDao {
    // ── embeddings ──
    @Upsert
    suspend fun upsertEmbedding(embedding: RecordEmbeddingEntity)

    @Query("SELECT * FROM record_embedding WHERE record_id = :recordId")
    suspend fun embedding(recordId: String): RecordEmbeddingEntity?

    /** Drops the embedding unless it was made for [textHash]. */
    @Query("DELETE FROM record_embedding WHERE record_id = :recordId AND text_hash != :textHash")
    suspend fun deleteStaleEmbedding(recordId: String, textHash: String)

    // ── analysis queue / state ──
    @Query("SELECT * FROM related_analysis WHERE record_id = :recordId")
    suspend fun analysis(recordId: String): RelatedAnalysisEntity?

    @Upsert
    suspend fun upsertAnalysis(analysis: RelatedAnalysisEntity)

    @Query("DELETE FROM related_analysis WHERE record_id = :recordId")
    suspend fun deleteAnalysis(recordId: String)

    /** The queue: PENDING, first queued first. */
    @Query("SELECT * FROM related_analysis WHERE status = 'PENDING' ORDER BY queued_at, record_id LIMIT 1")
    suspend fun nextPending(): RelatedAnalysisEntity?

    @Query("SELECT COUNT(*) FROM related_analysis WHERE status = 'PENDING'")
    suspend fun pendingCount(): Int

    /** Back to the queue (re-analysis needed); keeps the original queue time when already PENDING. */
    @Query(
        """
        UPDATE related_analysis
        SET status = 'PENDING', attempts = 0, error = NULL, updated_at = :now,
            queued_at = CASE WHEN status = 'PENDING' THEN queued_at ELSE :now END
        WHERE record_id IN (:recordIds)
        """,
    )
    suspend fun requeue(recordIds: List<String>, now: Long)

    /** After a process death: RUNNING rows are resumed from the queue (their cached judgments stay). */
    @Query("UPDATE related_analysis SET status = 'PENDING', updated_at = :now WHERE status = 'RUNNING'")
    suspend fun resetRunningToPending(now: Long): Int

    @Query("UPDATE related_analysis SET status = 'RUNNING', pipeline_version = :pipelineVersion, updated_at = :now WHERE record_id = :recordId")
    suspend fun markRunning(recordId: String, pipelineVersion: String, now: Long)

    @Query("UPDATE related_analysis SET status = 'DONE', error = NULL, completed_at = :now, updated_at = :now WHERE record_id = :recordId")
    suspend fun markDone(recordId: String, now: Long)

    @Query("UPDATE related_analysis SET status = 'FAILED', attempts = attempts + 1, error = :error, updated_at = :now WHERE record_id = :recordId")
    suspend fun markFailed(recordId: String, error: String, now: Long)

    // ── judgment cache ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJudgment(judgment: RelatedJudgmentEntity)

    @Query("SELECT * FROM related_judgment WHERE target_id = :targetId")
    suspend fun judgmentsFor(targetId: String): List<RelatedJudgmentEntity>

    /** Judgments of [targetId] made for another version of its text. */
    @Query("DELETE FROM related_judgment WHERE target_id = :targetId AND target_hash != :targetHash")
    suspend fun deleteStaleTargetJudgments(targetId: String, targetHash: String)

    /** Targets holding a judgment of [candidateId] made for another version of the candidate's text. */
    @Query("SELECT DISTINCT target_id FROM related_judgment WHERE candidate_id = :candidateId AND candidate_hash != :candidateHash")
    suspend fun targetsWithStaleCandidate(candidateId: String, candidateHash: String): List<String>

    @Query("DELETE FROM related_judgment WHERE candidate_id = :candidateId AND candidate_hash != :candidateHash")
    suspend fun deleteStaleCandidateJudgments(candidateId: String, candidateHash: String)

    // ── results ──
    /**
     * Label-2 candidates of a finished analysis, best first (similarity DESC, then id). Never label 1 / 0 / failed.
     * The text-version check and the ≤ 5 cut happen in `RelatedStore` (SQLite cannot hash the current text).
     */
    @Query(
        """
        SELECT j.candidate_id AS candidateId, j.similarity AS similarity, j.target_hash AS targetHash,
               j.candidate_hash AS candidateHash, a.text_hash AS analysisHash,
               t.text AS targetText, c.text AS candidateText
        FROM related_judgment j
        JOIN related_analysis a ON a.record_id = j.target_id
        JOIN records t ON t.id = j.target_id
        JOIN records c ON c.id = j.candidate_id
        WHERE j.target_id = :targetId AND a.status = 'DONE' AND a.pipeline_version = :pipelineVersion
          AND j.pipeline_version = :pipelineVersion AND j.status = 'OK' AND j.label = 2
        ORDER BY j.similarity DESC, j.candidate_id ASC
        """,
    )
    suspend fun label2Rows(targetId: String, pipelineVersion: String): List<RelatedResultRow>

    @Query(
        """
        SELECT j.candidate_id AS candidateId, j.similarity AS similarity, j.target_hash AS targetHash,
               j.candidate_hash AS candidateHash, a.text_hash AS analysisHash,
               t.text AS targetText, c.text AS candidateText
        FROM related_judgment j
        JOIN related_analysis a ON a.record_id = j.target_id
        JOIN records t ON t.id = j.target_id
        JOIN records c ON c.id = j.candidate_id
        WHERE j.target_id = :targetId AND a.status = 'DONE' AND a.pipeline_version = :pipelineVersion
          AND j.pipeline_version = :pipelineVersion AND j.status = 'OK' AND j.label = 2
        ORDER BY j.similarity DESC, j.candidate_id ASC
        """,
    )
    fun observeLabel2Rows(targetId: String, pipelineVersion: String): Flow<List<RelatedResultRow>>
}
