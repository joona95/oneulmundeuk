package app.oneulmundeuk.related

import app.oneulmundeuk.data.db.RecordDao
import app.oneulmundeuk.data.model.RecordWithCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the UI shows for related records: a target record and the past records that continue it.
 * Plain records only — the UI never sees the pipeline version, model ids, text hashes, labels or similarity.
 */
data class RelatedRecords(
    val target: RecordWithCategory,
    /** Stored order (most related first), 1..5. Never empty — no results = no [RelatedRecords]. */
    val related: List<RecordWithCategory>,
)

/**
 * The UI's entry point for stored related results (M6-4). Joins [RelatedStore] ids with the record Flow, so an
 * edit / delete / new DONE anywhere updates the save feedback, Detail and Related Memories without the UI doing anything.
 * Home does not use it: Home is time-based only ("다시 만난 생각").
 * PENDING / RUNNING / stale / 0 results → `null` (the section or card disappears; nothing old is shown meanwhile).
 */
class RelatedRepository(
    private val store: RelatedStore,
    private val records: RecordDao,
) {
    /** Results for [targetId] (save feedback grace window, Detail "이어지는 기록", Related Memories). */
    fun observeRelated(targetId: String): Flow<RelatedRecords?> =
        combine(store.observeResultIds(targetId), records.observeAll()) { ids, all -> resolveRelated(all, targetId, ids) }
            .distinctUntilChanged()
}

/**
 * Pure: ids → records in the given order, skipping records that no longer exist, duplicates and the target itself.
 * Null when the target is gone or nothing is left (never an empty list on screen).
 */
fun resolveRelated(all: List<RecordWithCategory>, targetId: String, relatedIds: List<String>): RelatedRecords? {
    val byId = all.associateBy { it.record.id }
    val target = byId[targetId] ?: return null
    val related = relatedIds.distinct().filter { it != targetId }.mapNotNull { byId[it] }
    return if (related.isEmpty()) null else RelatedRecords(target, related)
}
