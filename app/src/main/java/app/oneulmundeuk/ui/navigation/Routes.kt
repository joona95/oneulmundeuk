package app.oneulmundeuk.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

/** Start destination (M3). Home and Records are the two bottom-navigation tabs. */
@Serializable
object HomeRoute

@Serializable
object RecordListRoute

/** Explore (탐색) tab: "과거의 나에게 물어보세요." */
@Serializable
object ExploreRoute

/**
 * Semantic Search Results (inside the 탐색 tab). The question, plus [categoryHint] only when it came from an app category
 * suggestion (the engine puts that category's e5 Top 3 first). Typed questions never carry a hint.
 */
@Serializable
data class SearchResultsRoute(val query: String, val categoryHint: String? = null)

/** recordId == null → new record; otherwise edit that record. */
@Serializable
data class RecordEditorRoute(val recordId: String? = null)

@Serializable
data class RecordDetailRoute(val recordId: String)

/** Related Memories (M6-4): the target record id only; its stored results are read by the ViewModel. */
@Serializable
data class RelatedMemoriesRoute(val recordId: String)
