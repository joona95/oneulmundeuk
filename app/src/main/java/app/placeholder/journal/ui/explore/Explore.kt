package app.placeholder.journal.ui.explore

import app.placeholder.journal.data.db.CategoryEntity
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.search.SemanticSearchResult

/** Figma Explore / Semantic Search Results copy (design/figma-ui-builder: screens/explore.ts, data/copy.ts). */
object ExploreCopy {
    const val HERO = "과거의 나에게\n물어보세요."
    const val SUBTITLE = "단어가 아니라 의미로 찾아요. 정확한 표현이 기억나지 않아도 괜찮아요."
    const val SEARCH_PLACEHOLDER = "무엇이든 물어보세요"
    const val PRIVACY = "검색은 이 기기 안에서만 이루어져요"
    const val EXAMPLES_TITLE = "이렇게 물어볼 수 있어요"
    const val TOPICS_TITLE = "자주 등장한 주제"
    const val SORT_RELEVANCE = "관련도순"
    const val SORT_TIME = "시간순"
    fun found(count: Int) = "${count}개의 기록을 찾았어요"
    const val NOTHING_FOUND_TITLE = "관련된 기록을 찾지 못했어요"
    const val NOTHING_FOUND_BODY = "다른 말로 물어보세요."
    const val UNAVAILABLE_TITLE = "의미로 찾기를 준비하고 있어요"
    const val UNAVAILABLE_BODY = "곧 이 기기 안에서 기록을 의미로 찾아볼 수 있어요."
    const val SEARCHING = "기록을 살펴보고 있어요"
}

enum class SearchSort { Relevance, Time }

/** Search results screen. Never shows anything the engine did not return. */
sealed interface SearchResultsState {
    data object Loading : SearchResultsState

    /** No semantic engine yet: the quiet "준비하고 있어요" state (no count, no sort, no fake results). */
    data object Unavailable : SearchResultsState

    /** [items] in [sort] order (Relevance = engine order, Time = newest first). May be empty. */
    data class Results(val items: List<RecordWithCategory>, val sort: SearchSort) : SearchResultsState
}

object Explore {
    /** Trimmed query, or null when there is nothing to ask (the search does not run). */
    fun searchQuery(input: String): String? = input.trim().takeIf { it.isNotEmpty() }

    /**
     * 자주 등장한 주제 (MVP = the user's categories): only categories that have records, most used first; ties keep the
     * categories' own order ([categories] comes sorted by sort_order).
     */
    fun frequentTopics(records: List<RecordWithCategory>, categories: List<CategoryEntity>): List<CategoryEntity> {
        val counts = records.groupingBy { it.record.categoryId }.eachCount()
        return categories.filter { (counts[it.id] ?: 0) > 0 }.sortedByDescending { counts[it.id] ?: 0 }
    }

    /**
     * Pure: engine result → screen state. Found ids are resolved against the current records (deleted ones dropped,
     * duplicates ignored); [SearchSort.Relevance] keeps the engine's order, [SearchSort.Time] is newest first.
     */
    fun resultsState(result: SemanticSearchResult?, records: List<RecordWithCategory>, sort: SearchSort): SearchResultsState =
        when (result) {
            null -> SearchResultsState.Loading
            SemanticSearchResult.Unavailable -> SearchResultsState.Unavailable
            is SemanticSearchResult.Found -> {
                val byId = records.associateBy { it.record.id }
                val found = result.recordIds.distinct().mapNotNull { byId[it] }
                val ordered = when (sort) {
                    SearchSort.Relevance -> found
                    SearchSort.Time -> found.sortedWith(compareByDescending<RecordWithCategory> { it.record.createdAt }.thenBy { it.record.id })
                }
                SearchResultsState.Results(ordered, sort)
            }
        }
}
