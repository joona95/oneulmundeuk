package app.placeholder.journal.related

import app.placeholder.journal.data.db.RelatedDao
import app.placeholder.journal.data.db.RelatedResultRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Reads finished related results (docs/m6-related-design.md §3). Read side only; `RelatedAnalyzer` writes analyses.
 * Shown = label 2 of a DONE analysis for the ACTIVE pipeline version, whose target AND candidate hashes still match
 * the current texts → similarity DESC (ties: id) → at most [RelatedPolicy.MAX_RESULTS]. Never padded with 1 / 0.
 *
 * The active version is owned here (M6-4), never passed by the UI. `null` = no model in this build / runtime →
 * nothing is shown. Callers above this layer only see record ids (and `RelatedRepository` turns them into records).
 */
class RelatedStore(
    private val dao: RelatedDao,
    private val activePipelineVersion: String?,
) {
    suspend fun resultIds(targetId: String): List<String> =
        activePipelineVersion?.let { currentResults(dao.label2Rows(targetId, it)) } ?: emptyList()

    fun observeResultIds(targetId: String): Flow<List<String>> =
        activePipelineVersion?.let { dao.observeLabel2Rows(targetId, it).map(::currentResults) } ?: flowOf(emptyList())
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
