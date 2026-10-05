package app.placeholder.journal.ui.explore

import app.placeholder.journal.data.db.CategoryEntity
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.search.ExploreQuery
import app.placeholder.journal.search.TemporalParser
import app.placeholder.journal.util.TimeFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Explore "이렇게 물어볼 수 있어요" — deterministic templates from signals the search engine can actually answer.
 * No LLM question generation (experiments/search Thought Index PoC: all 4 generated questions failed grounding).
 * No aggregation questions ("요즘 자주 했던 생각은?", "예전과 지금 생각이 달라진 주제가 있나?") — the engine cannot answer them.
 *
 * 1. 날짜: each template is a pure date question (parses to [ExploreQuery.PureTemporal], answerable without a model);
 *    shown only when its period has ≥ [MIN_RECORDS_IN_PERIOD] records. At most [MAX_TEMPORAL], in the order below.
 * 2. category: "{이름}에 대해 예전엔 어떤 생각을 했지?" for categories with ≥ [MIN_CATEGORY_RECORDS] records written over
 *    ≥ [MIN_CATEGORY_SPAN_DAYS] days (a theme that keeps coming back), most records first. Needs e5 → only when
 *    [semanticAvailable]. A name that would itself parse as a date expression is skipped.
 * At most [MAX] in total; fewer (or none) when the signals are not there — nothing is padded.
 */
/** One suggestion. [categoryId] = the category a category template was made from (search hint); null for date templates. */
data class SuggestedQuestion(val text: String, val categoryId: String? = null)

object SuggestedQuestions {
    const val MAX = 4
    const val MAX_TEMPORAL = 2
    const val MIN_RECORDS_IN_PERIOD = 2
    const val MIN_CATEGORY_RECORDS = 3
    const val MIN_CATEGORY_SPAN_DAYS = 14

    val TEMPORAL_TEMPLATES = listOf(
        "작년 이맘때 무슨 생각을 했지?",
        "지난달에는 무슨 생각을 했지?",
        "작년 여름에는 무슨 생각을 했지?",
        "올해 초에는 무슨 생각을 했지?",
    )

    fun category(name: String) = "${name}에 대해 예전엔 어떤 생각을 했지?"

    fun build(
        records: List<RecordWithCategory>,
        categories: List<CategoryEntity>,
        semanticAvailable: Boolean,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<SuggestedQuestion> {
        val dates = records.map { it to TimeFormat.dayKey(it.record.createdAt, zone) }
        val temporal = TEMPORAL_TEMPLATES.filter { q ->
            val plan = ExploreQuery.of(q, today) as? ExploreQuery.PureTemporal ?: return@filter false
            dates.count { (_, d) -> d in plan.range } >= MIN_RECORDS_IN_PERIOD
        }.take(MAX_TEMPORAL).map { SuggestedQuestion(it) }

        val byCategory = dates.groupBy { (r, _) -> r.record.categoryId }
        val cats = if (!semanticAvailable) emptyList() else categories
            .filter { c ->
                val ds = byCategory[c.id].orEmpty().map { it.second }
                ds.size >= MIN_CATEGORY_RECORDS && ChronoUnit.DAYS.between(ds.min(), ds.max()) >= MIN_CATEGORY_SPAN_DAYS &&
                    TemporalParser.parse(category(c.name), today) == null
            }
            .sortedByDescending { byCategory[it.id].orEmpty().size } // stable: ties keep the categories' own order
            .map { SuggestedQuestion(category(it.name), it.id) }

        return (temporal + cats).distinctBy { it.text }.take(MAX)
    }
}
