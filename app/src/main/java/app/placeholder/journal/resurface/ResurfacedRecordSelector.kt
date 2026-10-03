package app.placeholder.journal.resurface

import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.util.TimeFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Home "다시 만난 생각": brings back ONE past record because time has passed — not because it is similar.
 * (Meaning-based connection after saving is a different job: see `related/RelatedRecordFinder`.)
 * Implementations must be deterministic for the same input (no Random) and must not throw.
 */
interface ResurfacedRecordSelector {
    fun select(records: List<RecordWithCategory>, today: LocalDate, zoneId: ZoneId): ResurfacedRecord?
}

data class ResurfacedRecord(val item: RecordWithCategory, val period: ResurfacePeriod)

/** Target points, in priority order. Labels are soft on purpose ("…쯤", never "32일 전"). */
enum class ResurfacePeriod(val label: String) {
    OneYear("1년 전쯤"),
    ThreeMonths("3개월 전쯤"),
    OneMonth("1개월 전쯤");

    fun target(today: LocalDate): LocalDate = when (this) {
        OneYear -> today.minusYears(1)
        ThreeMonths -> today.minusMonths(3)
        OneMonth -> today.minusMonths(1)
    }
}

/**
 * Date-only rule (no AI):
 * 1. Only records at least [minAgeDays] days old (a record you just wrote never comes back today).
 * 2. Try 1년 전 → 3개월 전 → 1개월 전; a record matches a period if its local date is within
 *    ±[windowDays] of that period's target date.
 * 3. Within the first period that has matches: closest to the target date, then the older record,
 *    then the smaller id — so the card is stable for the whole day.
 * Dates come from [TimeFormat.dayKey], the same rule the Records list and calendar use.
 */
class DateBasedResurfacedRecordSelector(
    private val minAgeDays: Long = 14,
    private val windowDays: Long = 7,
) : ResurfacedRecordSelector {

    override fun select(records: List<RecordWithCategory>, today: LocalDate, zoneId: ZoneId): ResurfacedRecord? {
        val dated = records
            .map { it to TimeFormat.dayKey(it.record.createdAt, zoneId) }
            .filter { (_, date) -> ChronoUnit.DAYS.between(date, today) >= minAgeDays }
        for (period in ResurfacePeriod.entries) {
            val target = period.target(today)
            val best = dated
                .filter { (_, date) -> abs(ChronoUnit.DAYS.between(target, date)) <= windowDays }
                .minWithOrNull(
                    compareBy<Pair<RecordWithCategory, LocalDate>>(
                        { (_, date) -> abs(ChronoUnit.DAYS.between(target, date)) },
                        { (item, _) -> item.record.createdAt },
                        { (item, _) -> item.record.id },
                    ),
                )
            if (best != null) return ResurfacedRecord(best.first, period)
        }
        return null
    }
}
