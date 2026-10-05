package app.placeholder.journal

import app.placeholder.journal.data.db.CategoryEntity
import app.placeholder.journal.data.db.RecordEntity
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.search.SemanticSearchResult
import app.placeholder.journal.search.UnavailableSemanticSearch
import app.placeholder.journal.ui.explore.Explore
import app.placeholder.journal.ui.explore.ExploreCopy
import app.placeholder.journal.ui.explore.SearchResultsState
import app.placeholder.journal.ui.explore.SearchSort
import app.placeholder.journal.ui.navigation.SearchResultsRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explore MVP ("과거의 나에게 물어보세요"): the search UI talks only to the SemanticSearch boundary — no keyword
 * fallback, no fake results while there is no engine. Pure parts only (JVM).
 */
class ExploreTest {
    private fun rec(id: String, at: Long, categoryId: String? = null) =
        RecordWithCategory(RecordEntity(id, "t-$id", at, at, null, categoryId, null), categoryName = null)

    private val a = rec("a", 100)
    private val b = rec("b", 300)
    private val c = rec("c", 200)
    private val all = listOf(b, c, a)

    @Test
    fun blankQuestionDoesNotSearchAndInputIsTrimmed() {
        assertNull(Explore.searchQuery(""))
        assertNull(Explore.searchQuery("  \n "))
        assertEquals("이직 고민", Explore.searchQuery("  이직 고민 "))
    }

    @Test
    fun noEngineMeansUnavailableNeverResults() = runTest {
        val result = UnavailableSemanticSearch.search("이직에 대해 어떤 생각을 했었지?")
        assertEquals(SemanticSearchResult.Unavailable, result)
        // Even with records that contain the words, nothing is shown: no keyword fallback.
        assertEquals(SearchResultsState.Unavailable, Explore.resultsState(result, all, SearchSort.Relevance))
        assertEquals(SearchResultsState.Loading, Explore.resultsState(null, all, SearchSort.Relevance))
    }

    @Test
    fun relevanceKeepsEngineOrderTimeIsNewestFirst() {
        val found = SemanticSearchResult.Found(listOf("a", "b", "c"))
        val relevance = Explore.resultsState(found, all, SearchSort.Relevance) as SearchResultsState.Results
        assertEquals(listOf("a", "b", "c"), relevance.items.map { it.record.id })
        val time = Explore.resultsState(found, all, SearchSort.Time) as SearchResultsState.Results
        assertEquals(listOf("b", "c", "a"), time.items.map { it.record.id })
        assertEquals(SearchSort.Time, time.sort)
    }

    @Test
    fun deletedRecordsAndDuplicatesAreDroppedAndEmptyIsKept() {
        val found = SemanticSearchResult.Found(listOf("gone", "c", "c", "a"))
        val s = Explore.resultsState(found, all, SearchSort.Relevance) as SearchResultsState.Results
        assertEquals(listOf("c", "a"), s.items.map { it.record.id })
        val none = Explore.resultsState(SemanticSearchResult.Found(emptyList()), all, SearchSort.Relevance) as SearchResultsState.Results
        assertTrue(none.items.isEmpty()) // → "관련된 기록을 찾지 못했어요"
    }

    @Test
    fun topicsAreUsedCategoriesMostFrequentFirst() {
        val categories = listOf(
            CategoryEntity("c1", "일상", 0, 0),
            CategoryEntity("c2", "커리어", 1, 0),
            CategoryEntity("c3", "공부", 2, 0), // no records → not a topic
        )
        val records = listOf(rec("1", 1, "c2"), rec("2", 2, "c1"), rec("3", 3, "c2"), rec("4", 4, null))
        assertEquals(listOf("c2", "c1"), Explore.frequentTopics(records, categories).map { it.id })
        val tie = listOf(rec("1", 1, "c2"), rec("2", 2, "c1"))
        assertEquals(listOf("c1", "c2"), Explore.frequentTopics(tie, categories).map { it.id }) // tie → category order
        assertTrue(Explore.frequentTopics(emptyList(), categories).isEmpty())
    }

    @Test
    fun figmaCopy() {
        assertEquals("과거의 나에게\n물어보세요.", ExploreCopy.HERO)
        assertEquals("검색은 이 기기 안에서만 이루어져요", ExploreCopy.PRIVACY)
        assertEquals("12개의 기록을 찾았어요", ExploreCopy.found(12))
    }

    @Test
    fun resultsRouteCarriesOnlyTheQuestionAndAnOptionalCategoryHint() {
        assertEquals(null, SearchResultsRoute("q").categoryHint) // typed questions: no hint
        assertEquals(listOf("query", "categoryHint"), SearchResultsRoute::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name })
    }
}
