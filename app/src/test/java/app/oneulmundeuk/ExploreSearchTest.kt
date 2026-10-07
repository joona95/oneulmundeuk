package app.oneulmundeuk

import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.db.RecordEmbeddingEntity
import app.oneulmundeuk.data.db.RecordEntity
import app.oneulmundeuk.data.db.RecordTextRow
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.search.ExploreQuery
import app.oneulmundeuk.search.ExploreSearch
import app.oneulmundeuk.search.SearchPolicy
import app.oneulmundeuk.search.SearchStorage
import app.oneulmundeuk.search.SemanticSearchResult
import app.oneulmundeuk.search.TemporalParser
import app.oneulmundeuk.ui.explore.Explore
import app.oneulmundeuk.ui.explore.SearchResultsState
import app.oneulmundeuk.ui.explore.SearchSort
import app.oneulmundeuk.ui.explore.SuggestedQuestions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Explore production search: e5 ranking + deterministic date routing (port of experiments/search/s2_temporal.py), no LLM. */
class ExploreSearchTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 5)
    private fun at(date: String, hour: Int = 12) = LocalDateTime.parse("${date}T%02d:00".format(hour)).atZone(zone).toInstant().toEpochMilli()

    private class MemoryStorage : SearchStorage {
        val records = linkedMapOf<String, RecordTextRow>()
        val categories = mutableMapOf<String, String>()
        val embeddings = mutableMapOf<Pair<String, String>, RecordEmbeddingEntity>()
        override suspend fun allRecords() = records.values.toList()
        override suspend fun recordIdsInCategory(categoryId: String) = categories.filterValues { it == categoryId }.keys.toList()
        override suspend fun embedding(recordId: String, modelId: String) = embeddings[recordId to modelId]
        override suspend fun saveEmbedding(embedding: RecordEmbeddingEntity) { embeddings[embedding.recordId to embedding.modelId] = embedding }
    }

    /** "s=0.80" in a text → cosine ≈ 0.80 to any question (vector (s, √(1-s²)) vs (1, 0)). Records the texts it embeds. */
    private val queries = mutableListOf<String>()
    private val embedder = FakeEmbedder(vectors = { text ->
        val s = Regex("s=([0-9.]+)").find(text)?.groupValues?.get(1)?.toFloat() ?: 1f
        floatArrayOf(s, kotlin.math.sqrt(1 - s * s))
    }).let { base ->
        object : app.oneulmundeuk.related.TextEmbedder by base {
            override suspend fun embedQuery(query: String): FloatArray { queries += query; return floatArrayOf(1f, 0f) }
        }
    }

    private fun storage(vararg rows: Triple<String, String, String>) = MemoryStorage().apply {
        rows.forEach { (id, date, text) -> records[id] = RecordTextRow(id, text, at(date)) }
    }

    private fun search(st: SearchStorage, withModel: Boolean = true) =
        ExploreSearch(st, if (withModel) embedder else null, zone, today = { today })

    // ── parser ──
    private fun parsed(q: String) = TemporalParser.parse(q, today)!!

    @Test
    fun everySupportedExpressionHasItsRange() {
        fun check(q: String, rule: String, start: String, end: String, remainder: String, contentFree: Boolean) {
            val t = parsed(q)
            assertEquals(q, listOf(rule, start, end, remainder, contentFree),
                listOf(t.range.rule, t.range.start.toString(), t.range.end.toString(), t.remainder, t.contentFree))
        }
        check("작년 이맘때 무슨 생각을 했지?", "작년 이맘때", "2025-09-05", "2025-11-04", "무슨 생각을 했지?", true)
        check("지난달에 무슨 일이 있었지?", "지난달", "2026-09-01", "2026-09-30", "무슨 일이 있었지?", true)
        check("올해 초에 세운 목표", "올해 초", "2026-01-01", "2026-02-28", "세운 목표", false)
        check("작년 여름에 운동했던 기록", "작년 여름", "2025-06-01", "2025-08-31", "운동했던 기록", false)
        check("올봄에 회사 일은 어땠지?", "올봄", "2026-03-01", "2026-05-31", "회사 일은 어땠지?", false)
        check("올해 봄 기록", "올봄", "2026-03-01", "2026-05-31", "기록", true)
        check("작년 고민", "작년", "2025-01-01", "2025-12-31", "고민", false)
        check("올해 운동", "올해", "2026-01-01", "2026-10-05", "운동", false)
        assertEquals(LocalDate.of(2026, 2, 1), TemporalParser.parse("지난달", LocalDate.of(2026, 3, 10))!!.range.start)
        assertEquals(LocalDate.of(2025, 12, 31), TemporalParser.parse("지난달", LocalDate.of(2026, 1, 15))!!.range.end)
        assertEquals(LocalDate.of(2027, 2, 28), TemporalParser.parse("작년 이맘때", LocalDate.of(2028, 2, 29))!!.range.anchor)
    }

    @Test
    fun unknownExpressionsAreNotGuessed() {
        listOf("어제 무슨 생각을 했지?", "3월에 쓴 기록", "지난주 기록", "새 회사 들어가서 처음 적응할 때", "사이드 프로젝트가 재미있었던 때")
            .forEach { assertTrue(it, ExploreQuery.of(it, today) is ExploreQuery.Semantic) }
    }

    // ── engine ──
    @Test
    fun semanticSearchIsE5OrderTop10() = runTest {
        val st = storage(*(1..12).map { Triple("r%02d".format(it), "2026-0${1 + it % 9}-01", "기록 s=0.${10 + it * 5}") }.toTypedArray())
        val r = search(st).search("  사이드 프로젝트가 재미있었던 때 ") as SemanticSearchResult.Found
        assertEquals(SearchPolicy.MAX_RESULTS, r.recordIds.size)
        assertEquals(listOf("r12", "r11", "r10"), r.recordIds.take(3)) // highest similarity first
        assertEquals("사이드 프로젝트가 재미있었던 때", queries.single()) // whole question, normalized
        assertEquals(12, st.embeddings.size) // record vectors cached in the shared table
    }

    @Test
    fun unknownTemporalExpressionFallsBackToPlainE5() = runTest {
        val st = storage(Triple("a", "2026-10-04", "어제 s=0.90"), Triple("b", "2024-01-01", "옛날 s=0.95"))
        assertEquals(listOf("b", "a"), (search(st).search("어제 무슨 생각을 했지?") as SemanticSearchResult.Found).recordIds)
        assertEquals("어제 무슨 생각을 했지?", queries.single())
    }

    @Test
    fun pureTemporalIsDateOrderedAndNeedsNoModel() = runTest {
        val st = storage(
            Triple("near", "2025-10-04", "x"), Triple("before", "2025-09-27", "x"), Triple("after", "2025-10-06", "x"),
            Triple("outside", "2025-12-01", "x"), Triple("sep1", "2026-09-03", "x"), Triple("sep2", "2026-09-20", "x"),
        )
        // 이맘때: closest to 2025-10-05 first (ties: older) — works without e5
        assertEquals(listOf("near", "after", "before"),
            (search(st, withModel = false).search("작년 이맘때 무슨 생각을 했지?") as SemanticSearchResult.Found).recordIds)
        // 지난달: newest first
        assertEquals(listOf("sep2", "sep1"), (search(st, withModel = false).search("지난달에 무슨 일이 있었지?") as SemanticSearchResult.Found).recordIds)
        assertTrue(queries.isEmpty())
    }

    @Test
    fun temporalPlusMeaningFiltersByDateThenRanksTheRestOfTheQuestion() = runTest {
        val st = storage(
            Triple("in-low", "2025-07-01", "운동 s=0.20"), Triple("in-high", "2025-08-15", "달리기 s=0.90"),
            Triple("out-high", "2025-09-10", "달리기 s=0.99"),
        )
        assertEquals(listOf("in-high", "in-low"), (search(st).search("작년 여름에 운동했던 기록") as SemanticSearchResult.Found).recordIds)
        assertEquals("운동했던 기록", queries.single()) // date words removed before e5
        assertEquals(SemanticSearchResult.Found(emptyList()), search(st).search("올봄에 운동한 기록")) // empty period → 0, not widened
    }

    @Test
    fun withoutModelOnlyE5QuestionsAreUnavailable() = runTest {
        val st = storage(Triple("a", "2026-09-10", "x s=0.5"))
        assertEquals(SemanticSearchResult.Unavailable, search(st, withModel = false).search("사이드 프로젝트"))
        assertEquals(SemanticSearchResult.Unavailable, search(st, withModel = false).search("작년 여름에 운동했던 기록"))
        assertEquals(SemanticSearchResult.Found(listOf("a")), search(st, withModel = false).search("지난달에 무슨 일이 있었지?"))
        assertEquals(SemanticSearchResult.Found(emptyList()), search(st).search("   "))
    }

    @Test
    fun relevanceAndTimeSortShowTheSameResultSet() {
        fun rec(id: String, date: String) = RecordWithCategory(RecordEntity(id, id, at(date), at(date), null, null, null), null)
        val all = listOf(rec("a", "2025-01-01"), rec("b", "2026-01-01"), rec("c", "2025-06-01"))
        val found = SemanticSearchResult.Found(listOf("c", "a", "b"))
        val rel = Explore.resultsState(found, all, SearchSort.Relevance) as SearchResultsState.Results
        val time = Explore.resultsState(found, all, SearchSort.Time) as SearchResultsState.Results
        assertEquals(listOf("c", "a", "b"), rel.items.map { it.record.id })
        assertEquals(listOf("b", "c", "a"), time.items.map { it.record.id })
        assertEquals(rel.items.toSet(), time.items.toSet())
    }

    // ── suggested questions ──
    private val cats = listOf(CategoryEntity("c1", "커리어", 0, 0), CategoryEntity("c2", "취미", 1, 0), CategoryEntity("c3", "관계", 2, 0))
    private fun rec(id: String, date: String, cat: String? = null) = RecordWithCategory(RecordEntity(id, "t", at(date), at(date), null, cat, null), null)

    private val records = listOf(
        rec("1", "2025-10-01", "c1"), rec("2", "2025-10-20", "c1"), rec("3", "2026-09-05", "c1"), rec("4", "2026-09-25", "c1"),
        rec("5", "2026-03-01", "c2"), rec("6", "2026-03-20", "c2"), rec("7", "2026-04-15", "c2"),
        rec("8", "2026-05-01", "c3"), rec("9", "2026-05-02", "c3"), rec("10", "2026-05-03", "c3"), // 3 records within 2 days
    )

    @Test
    fun suggestionsAreDateAndCategoryTemplatesAtMostFour() {
        val suggestions = SuggestedQuestions.build(records, cats, semanticAvailable = true, today = today, zone = zone)
        assertEquals(listOf(null, null, "c1", "c2"), suggestions.map { it.categoryId }) // category templates carry their id
        val qs = suggestions.map { it.text }
        assertEquals(
            listOf("작년 이맘때 무슨 생각을 했지?", "지난달에는 무슨 생각을 했지?", "커리어에 대해 예전엔 어떤 생각을 했지?", "취미에 대해 예전엔 어떤 생각을 했지?"),
            qs,
        )
        assertTrue(qs.size <= SuggestedQuestions.MAX)
        assertFalse(qs.any { "관계" in it }) // 3 records in 2 days: not a recurring theme
    }

    @Test
    fun suggestionsAreAnswerableByTheEngine() {
        val qs = SuggestedQuestions.build(records, cats, semanticAvailable = true, today = today, zone = zone).map { it.text }
        qs.filter { "무슨 생각을 했지" in it }.forEach { assertTrue(it, ExploreQuery.of(it, today) is ExploreQuery.PureTemporal) }
        qs.filter { "에 대해" in it }.forEach { assertTrue(it, ExploreQuery.of(it, today) is ExploreQuery.Semantic) }
        SuggestedQuestions.TEMPORAL_TEMPLATES.forEach { assertTrue(it, ExploreQuery.of(it, today) is ExploreQuery.PureTemporal) }
        val aggregation = listOf("반복", "자주", "달라", "바뀐", "변했", "변화", "요즘")
        assertTrue(qs.none { q -> aggregation.any { it in q } })
    }

    @Test
    fun noModelMeansOnlyDateQuestionsAndFewRecordsMeansFewOrNone() {
        assertEquals(listOf("작년 이맘때 무슨 생각을 했지?", "지난달에는 무슨 생각을 했지?"),
            SuggestedQuestions.build(records, cats, semanticAvailable = false, today = today, zone = zone).map { it.text })
        assertTrue(SuggestedQuestions.build(records.take(1), cats, true, today, zone).isEmpty())
        assertTrue(SuggestedQuestions.build(emptyList(), cats, true, today, zone).isEmpty())
    }

    @Test
    fun categoryNameThatLooksLikeADateIsSkipped() {
        val odd = listOf(CategoryEntity("c9", "작년", 0, 0))
        val rs = listOf(rec("a", "2026-01-01", "c9"), rec("b", "2026-02-01", "c9"), rec("c", "2026-03-01", "c9"))
        assertNull(SuggestedQuestions.build(rs, odd, true, today, zone).firstOrNull { "에 대해" in it.text })
    }

    // ── category hint (app category suggestion only) ──
    private val hinted = "취미에 대해 예전엔 어떤 생각을 했지?"

    /** 12 records: hobby h1..h5 (similarity .30–.50, lower than most others), others o1..o7 (.55–.90). */
    private fun hintStorage() = storage(
        *(1..5).map { Triple("h$it", "2026-0$it-01", "취미 s=0.${25 + it * 5}") }.toTypedArray(),
        *(1..7).map { Triple("o$it", "2025-0$it-01", "다른 s=0.${50 + it * 5}") }.toTypedArray(),
    ).apply { (1..5).forEach { categories["h$it"] = "hobby" }; (1..7).forEach { categories["o$it"] = "work" } }

    @Test
    fun categorySuggestionPutsTheCategoryTop3FirstThenFillsFromGlobalE5() = runTest {
        val ids = (search(hintStorage()).search(hinted, categoryHint = "hobby") as SemanticSearchResult.Found).recordIds
        assertEquals(listOf("h5", "h4", "h3"), ids.take(3)) // category's own e5 order, no score boost
        assertEquals(listOf("o7", "o6", "o5", "o4", "o3", "o2", "o1"), ids.drop(3)) // then global e5 order
        assertEquals(10, ids.size)
        assertEquals(ids.size, ids.toSet().size) // no duplicates
        assertTrue(ids.any { it.startsWith("o") }) // semantic results outside the category stay
    }

    @Test
    fun globalFillSkipsTheHeadButKeepsLowerCategoryRecordsInPlace() {
        val ranked = listOf("o1", "h1", "o2", "h2", "h3", "h4", "o3")
        assertEquals(listOf("h1", "h2", "h3", "o1", "o2", "h4", "o3"),
            app.oneulmundeuk.search.withCategoryHint(ranked, setOf("h1", "h2", "h3", "h4")))
        assertEquals(listOf("h1", "o1", "o2"), app.oneulmundeuk.search.withCategoryHint(listOf("o1", "h1", "o2"), setOf("h1"))) // < 3 in category
        assertEquals(listOf("o1"), app.oneulmundeuk.search.withCategoryHint(listOf("o1"), emptySet()))
    }

    @Test
    fun typedSameSentenceGetsNoCategoryHint() = runTest {
        val st = hintStorage()
        val typed = (search(st).search(hinted) as SemanticSearchResult.Found).recordIds
        assertEquals(listOf("o7", "o6", "o5", "o4", "o3", "o2", "o1", "h5", "h4", "h3"), typed) // plain global e5
    }

    @Test
    fun fewerThanTenRecordsAreFine() = runTest {
        val st = storage(Triple("h1", "2026-01-01", "취미 s=0.20"), Triple("o1", "2026-02-01", "다른 s=0.90"))
            .apply { categories["h1"] = "hobby"; categories["o1"] = "work" }
        assertEquals(listOf("h1", "o1"), (search(st).search(hinted, "hobby") as SemanticSearchResult.Found).recordIds)
        assertEquals(SemanticSearchResult.Found(emptyList()), search(storage()).search(hinted, "hobby"))
    }

    @Test
    fun hintDoesNotChangeTemporalRouting() = runTest {
        val st = hintStorage()
        assertEquals(search(st).search("작년 여름에 운동했던 기록"), search(st).search("작년 여름에 운동했던 기록", "hobby"))
    }
}
