package app.placeholder.journal.related

import app.placeholder.journal.data.model.RecordWithCategory

/**
 * Finds past records connected to [record] ("문득, 예전의 생각이 떠올랐어요").
 * Implementations are swappable for experiments (keyword, on-device embedding, small LLM rerank…).
 * Contract: on-device only, results exclude [record] itself, newest-relevant first, never throws for "no result".
 * No AI implementation and no embedding schema exist yet — see [NoOpRelatedRecordFinder].
 */
interface RelatedRecordFinder {
    suspend fun findRelated(record: RecordWithCategory, limit: Int = 4): List<RecordWithCategory>
}

/** Default until an AI implementation lands: nothing is related, so no Related Memories screen is shown. */
class NoOpRelatedRecordFinder : RelatedRecordFinder {
    override suspend fun findRelated(record: RecordWithCategory, limit: Int): List<RecordWithCategory> = emptyList()
}
