package app.placeholder.journal.search

/**
 * Explore "과거의 나에게 물어보세요" — the ONE boundary between the search UI and the meaning-based search engine.
 *
 * The UI promises search by MEANING, so nothing here may fall back to keyword / LIKE matching. The engine
 * ([ExploreSearch]: e5 + deterministic date routing, no LLM) runs on device only: the question and the records never
 * leave it. Without a local model, questions that need e5 return [SemanticSearchResult.Unavailable] and the results
 * screen shows its quiet "준비하고 있어요" state — never fake results.
 */
interface SemanticSearch {
    /**
     * [categoryHint] = categoryId of an app-generated category suggestion ("{category}에 대해 예전엔 어떤 생각을 했지?") —
     * only the suggestion chip passes it; text the user typed never carries a hint (even the same sentence).
     */
    suspend fun search(query: String, categoryHint: String? = null): SemanticSearchResult
}

sealed interface SemanticSearchResult {
    /** No local model / it cannot run right now. */
    data object Unavailable : SemanticSearchResult

    /** Record ids, most relevant first. Empty = nothing related. */
    data class Found(val recordIds: List<String>) : SemanticSearchResult
}

/** Always unavailable (tests / previews). The app uses [ExploreSearch], which is unavailable only for e5 questions. */
object UnavailableSemanticSearch : SemanticSearch {
    override suspend fun search(query: String, categoryHint: String?): SemanticSearchResult = SemanticSearchResult.Unavailable
}
