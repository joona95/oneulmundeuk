package app.placeholder.journal.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

/** Start destination (M3). Home and Records are the two bottom-navigation tabs. */
@Serializable
object HomeRoute

@Serializable
object RecordListRoute

/** recordId == null → new record; otherwise edit that record. */
@Serializable
data class RecordEditorRoute(val recordId: String? = null)

@Serializable
data class RecordDetailRoute(val recordId: String)

/**
 * Related Memories. M4 opened it right after saving; M6-1 removed that, and M6-4 changes this route to
 * `recordId` only (results read from storage). Until then [relatedIds] keeps the given order, joined with ','.
 */
@Serializable
data class RelatedMemoriesRoute(val recordId: String, val relatedIds: String) {
    val relatedIdList: List<String> get() = relatedIds.split(',').filter { it.isNotEmpty() }

    companion object {
        fun of(recordId: String, relatedIds: List<String>) = RelatedMemoriesRoute(recordId, relatedIds.joinToString(","))
    }
}
