package app.placeholder.journal.related

import app.placeholder.journal.data.db.RelatedDao
import app.placeholder.journal.data.db.RelatedResultRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reads finished related results (docs/m6-related-design.md §3). Read side only; M6-3 writes analyses.
 * Shown = label 2 of a DONE analysis for [pipelineVersion], whose target AND candidate hashes still match the
 * current texts → similarity DESC (ties: id) → at most [RelatedPolicy.MAX_RESULTS]. Never padded with 1 / 0.
 */
class RelatedStore(private val dao: RelatedDao) {
    suspend fun resultIds(targetId: String, pipelineVersion: String): List<String> =
        currentResults(dao.label2Rows(targetId, pipelineVersion))

    fun observeResultIds(targetId: String, pipelineVersion: String): Flow<List<String>> =
        dao.observeLabel2Rows(targetId, pipelineVersion).map(::currentResults)
}

/**
 * Pure: keep rows whose stored text versions match the current texts, in the given (similarity) order, then cut.
 * A stale pair is dropped, never replaced by a lower-ranked label 1 / 0.
 */
fun currentResults(rows: List<RelatedResultRow>, limit: Int = RelatedPolicy.MAX_RESULTS): List<String> =
    rows.asSequence()
        .filter { row ->
            val target = RelatedText.hash(row.targetText)
            row.targetHash == target && row.analysisHash == target && row.candidateHash == RelatedText.hash(row.candidateText)
        }
        .map { it.candidateId }
        .distinct()
        .take(limit)
        .toList()
