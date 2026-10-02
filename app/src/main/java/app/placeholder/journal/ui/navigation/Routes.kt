package app.placeholder.journal.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

@Serializable
object RecordListRoute

/** recordId == null → new record; otherwise edit that record. */
@Serializable
data class RecordEditorRoute(val recordId: String? = null)

@Serializable
data class RecordDetailRoute(val recordId: String)
