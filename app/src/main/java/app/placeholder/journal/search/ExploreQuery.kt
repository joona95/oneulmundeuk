package app.placeholder.journal.search

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Explore query routing (production decision after experiments/search S1 · S2 · Thought Index PoC).
 *
 *   시간 표현 없음            → [ExploreQuery.Semantic]: e5 similarity 순
 *   시간 표현 + 의미 내용      → [ExploreQuery.TemporalSemantic]: 날짜 범위 안에서 e5 similarity 순
 *   시간 표현만 (내용 없음)    → [ExploreQuery.PureTemporal]: 날짜 범위 안 기록을 날짜 순 (모델 불필요)
 *
 * Deterministic port of experiments/search/s2_temporal.py (that file stays frozen). No LLM.
 * TODO(explore): topic이 명시된 recurring / change 질문 ("잠 못 드는 밤이 반복됐던 때", "달리기에 대한 마음이 변했나?")은
 *   나중에 topic 추출 → e5 high-recall 후보 → 작성일 순 multi-record Qwen 1회로 다룰 새 variant를 여기에 추가한다.
 *   지금은 실제 Qwen runtime이 없으므로 [ExploreQuery.Semantic]으로 처리된다 (docs/TODO.md).
 */
sealed interface ExploreQuery {
    data class Semantic(val text: String) : ExploreQuery
    data class TemporalSemantic(val range: TemporalRange, val text: String) : ExploreQuery
    data class PureTemporal(val range: TemporalRange) : ExploreQuery

    companion object {
        fun of(query: String, today: LocalDate): ExploreQuery {
            val t = TemporalParser.parse(query, today) ?: return Semantic(query.trim())
            return if (t.contentFree) PureTemporal(t.range) else TemporalSemantic(t.range, t.remainder)
        }
    }
}

/** Inclusive local-date range. [anchor] = "이맘때"의 기준일 (가까운 순 정렬). */
data class TemporalRange(val rule: String, val start: LocalDate, val end: LocalDate, val anchor: LocalDate? = null) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

data class ParsedTemporal(val range: TemporalRange, val remainder: String, val contentFree: Boolean)

/**
 * 지원 표현 (질문에서 처음 일치한 하나, 위에서부터 우선). 기준일 = 오늘 (기기 시간대).
 *
 * | 표현 | 범위 (양 끝 포함) |
 * | 작년 이맘때 | (오늘 - 1년) ± 30일 |
 * | 지난달 | 전월 1일 ~ 말일 |
 * | 올해 초 | 올해 1월 1일 ~ 2월 말일 |
 * | 작년 여름 | 작년 6월 1일 ~ 8월 31일 |
 * | 올봄 / 올해 봄 | 올해 3월 1일 ~ 5월 31일 |
 * | 작년 | 작년 1월 1일 ~ 12월 31일 |
 * | 올해 | 올해 1월 1일 ~ 오늘 |
 *
 * 시간 표현과 바로 뒤 조사(에는/에도/에/의)를 지운 나머지가 의미 내용. 질문 틀([STOP])과 문장부호만 남으면 content-free.
 * 그 밖의 표현(어제, 3월, 지난주 …)은 해석하지 않는다 → 일반 의미 검색.
 */
object TemporalParser {
    val STOP = setOf(
        "무슨", "무엇", "뭐", "생각", "생각을", "생각이", "했지", "했었지", "일", "일이", "일은", "있었지", "어땠지", "어땠더라",
        "뭐였지", "기록", "기록들", "했던", "있었던", "때", "때는", "나", "내가",
    )
    private const val PARTICLE = "(?:에는|에도|에|의)?"

    private fun rules(today: LocalDate): List<Triple<String, Regex, TemporalRange>> {
        val y = today.year
        val lastMonthEnd = today.withDayOfMonth(1).minusDays(1)
        val anchor = today.minusYears(1) // 2월 29일 → 2월 28일 (java.time)
        fun r(name: String, pattern: String, start: LocalDate, end: LocalDate, anchor: LocalDate? = null) =
            Triple(name, Regex("(?:$pattern)$PARTICLE"), TemporalRange(name, start, end, anchor))
        return listOf(
            r("작년 이맘때", "작년\\s*이맘때", anchor.minusDays(30), anchor.plusDays(30), anchor),
            r("지난달", "지난\\s*달", lastMonthEnd.withDayOfMonth(1), lastMonthEnd),
            r("올해 초", "올해\\s*초", LocalDate.of(y, 1, 1), LocalDate.of(y, 2, 1).plusMonths(1).minusDays(1)),
            r("작년 여름", "작년\\s*여름", LocalDate.of(y - 1, 6, 1), LocalDate.of(y - 1, 8, 31)),
            r("올봄", "올\\s*봄|올해\\s*봄", LocalDate.of(y, 3, 1), LocalDate.of(y, 5, 31)),
            r("작년", "작년", LocalDate.of(y - 1, 1, 1), LocalDate.of(y - 1, 12, 31)),
            r("올해", "올해", LocalDate.of(y, 1, 1), today),
        )
    }

    fun contentWords(text: String): List<String> =
        text.replace(Regex("[^\\p{L}\\p{N}_\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() && it !in STOP }

    fun parse(query: String, today: LocalDate): ParsedTemporal? {
        for ((_, regex, range) in rules(today)) {
            val m = regex.find(query) ?: continue
            val remainder = (query.substring(0, m.range.first) + " " + query.substring(m.range.last + 1)).replace(Regex("\\s+"), " ").trim()
            return ParsedTemporal(range, remainder, contentWords(remainder).isEmpty())
        }
        return null
    }

    /** Pure-temporal order: "이맘때" = closest to the anchor (ties: older, id); otherwise newest first (ties: id). */
    fun <T> dateOrder(range: TemporalRange, items: List<T>, date: (T) -> LocalDate, createdAt: (T) -> Long, id: (T) -> String): List<T> =
        if (range.anchor != null) {
            items.sortedWith(compareBy<T>({ Math.abs(ChronoUnit.DAYS.between(range.anchor, date(it))) }, { createdAt(it) }, { id(it) }))
        } else {
            items.sortedWith(compareByDescending<T> { createdAt(it) }.thenByDescending { id(it) })
        }
}
