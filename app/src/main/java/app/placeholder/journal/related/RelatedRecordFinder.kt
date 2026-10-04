package app.placeholder.journal.related

import app.placeholder.journal.data.model.RecordWithCategory

/**
 * Finds past records connected to [record] ("문득, 예전의 생각이 떠올랐어요").
 * Implementations are swappable for experiments (keyword, on-device embedding, small LLM rerank…).
 * Contract: on-device only, results exclude [record] itself, most related first, at most [limit],
 * only records worth showing (never padded up to [limit]), never throws for "no result".
 * No AI implementation and no embedding schema exist yet — see [NoOpRelatedRecordFinder]. (M5)
 */
interface RelatedRecordFinder {
    suspend fun findRelated(record: RecordWithCategory, limit: Int = LIMIT): List<RecordWithCategory>

    companion object {
        /** Related Memories shows the record just saved + at most this many past records. */
        const val LIMIT = 5
    }
}

/** Default until a real implementation lands (M5): nothing is related, so no Related Memories screen is shown. */
class NoOpRelatedRecordFinder : RelatedRecordFinder {
    override suspend fun findRelated(record: RecordWithCategory, limit: Int): List<RecordWithCategory> = emptyList()
}

/**
 * What the app does with a finder's answer: keep its order, drop the saved record itself and duplicates,
 * cap at [limit]. Pure, so the rule is unit-tested without a database.
 */
fun relatedIdsToShow(savedId: String, found: List<RecordWithCategory>, limit: Int = RelatedRecordFinder.LIMIT): List<String> =
    found.asSequence().map { it.record.id }.filter { it != savedId }.distinct().take(limit).toList()
