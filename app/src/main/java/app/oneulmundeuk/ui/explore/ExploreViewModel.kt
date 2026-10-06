package app.oneulmundeuk.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.search.SemanticSearch
import app.oneulmundeuk.search.SemanticSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Explore entry. Topics and suggested questions are pure functions of the Room record / category Flows
 * ([SuggestedQuestions]: deterministic templates, no LLM), so they are current at once and never block.
 */
class ExploreViewModel(
    repository: RecordRepository,
    semanticAvailable: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
    today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    /** 자주 등장한 주제 — hidden when empty. */
    val topics: StateFlow<List<CategoryEntity>> = combine(repository.observeRecords(), repository.observeCategories(), Explore::frequentTopics)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 이렇게 물어볼 수 있어요 — 0..4 answerable questions (hidden when empty). */
    val questions: StateFlow<List<SuggestedQuestion>> = combine(repository.observeRecords(), repository.observeCategories()) { records, categories ->
        SuggestedQuestions.build(records, categories, semanticAvailable, today(), zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Semantic Search Results. Asks [search] once per query (the engine decides relevance; nothing here matches text),
 * then joins the ids with the Room record Flow so edits / deletes made in Detail show up on return.
 */
class SearchResultsViewModel(
    private val search: SemanticSearch,
    repository: RecordRepository,
    initialQuery: String,
    /** Set only when the search came from an app category suggestion; dropped as soon as the user types a new question. */
    private val initialCategoryHint: String? = null,
) : ViewModel() {
    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    private val result = MutableStateFlow<SemanticSearchResult?>(null)
    private val sort = MutableStateFlow(SearchSort.Relevance)
    private var running: Job? = null // declared before init, which starts the first search

    val state: StateFlow<SearchResultsState> = combine(result, repository.observeRecords(), sort, Explore::resultsState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResultsState.Loading)

    init {
        run(initialQuery, initialCategoryHint)
    }

    /** A new question from the results screen's own search bar (blank → ignored). */
    fun submit(input: String) {
        val q = Explore.searchQuery(input) ?: return
        _query.value = q
        sort.value = SearchSort.Relevance
        run(q, categoryHint = null) // typed text: never a category hint
    }

    fun setSort(value: SearchSort) = sort.update { value }

    private fun run(q: String, categoryHint: String?) {
        running?.cancel() // an older question never overwrites a newer one
        result.value = null
        running = viewModelScope.launch {
            // An engine error is "local AI unavailable", never an error screen or partial results.
            result.value = try {
                search.search(q, categoryHint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SemanticSearchResult.Unavailable
            }
        }
    }
}
