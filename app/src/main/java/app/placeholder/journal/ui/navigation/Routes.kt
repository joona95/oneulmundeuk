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

/** Related Memories (M6-4): the target record id only; its stored results are read by the ViewModel. */
@Serializable
data class RelatedMemoriesRoute(val recordId: String)
