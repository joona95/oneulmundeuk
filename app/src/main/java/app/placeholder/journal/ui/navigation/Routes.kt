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
 * Related Memories (M4), right after saving a new record. [relatedIds] keeps the finder's order (most related
 * first, ≤ 5), joined with ',' so the route only carries plain strings (record ids are UUIDs).
 */
@Serializable
data class RelatedMemoriesRoute(val recordId: String, val relatedIds: String) {
    val relatedIdList: List<String> get() = relatedIds.split(',').filter { it.isNotEmpty() }

    companion object {
        fun of(recordId: String, relatedIds: List<String>) = RelatedMemoriesRoute(recordId, relatedIds.joinToString(","))
    }
}
