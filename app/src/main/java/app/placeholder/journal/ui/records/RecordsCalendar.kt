package app.placeholder.journal.ui.records

import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.util.TimeFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Pure calendar / grouping / filter logic for the Records screen (no Android, no Compose — unit-tested).
 * The data set is small (a personal journal), so everything is derived in memory from the one
 * `observeRecords()` Flow; no month-specific DAO query is needed.
 */
object RecordsCalendar {
    /** At most this many emotion dots under a calendar day. */
    const val MAX_DOTS = 3

    /**
     * Sunday-first month grid. `null` = blank cell before the 1st / after the last day.
     * The size is always a multiple of 7 (4–6 weeks).
     */
    fun monthCells(month: YearMonth): List<LocalDate?> {
        val lead = month.atDay(1).dayOfWeek.value % 7 // Mon=1 … Sun=7 → Sun=0
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells = List<LocalDate?>(lead) { null } + days
        val trail = (7 - cells.size % 7) % 7
        return cells + List<LocalDate?>(trail) { null }
    }

    /** `null` category = 전체. A specific category keeps only its records (미분류 records are excluded). */
    fun filterByCategory(records: List<RecordWithCategory>, categoryId: String?): List<RecordWithCategory> =
        if (categoryId == null) records else records.filter { it.record.categoryId == categoryId }

    /** Records per local day (device zone via [TimeFormat.dayKey]); each day oldest → newest. */
    fun groupByDay(
        records: List<RecordWithCategory>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, List<RecordWithCategory>> =
        records.groupBy { TimeFormat.dayKey(it.record.createdAt, zone) }
            .mapValues { (_, items) -> items.sortedBy { it.record.createdAt } }

    /**
     * Dots for one day: the first [MAX_DOTS] records in time order, one dot each.
     * `null` = a record without an emotion (drawn as a neutral dot, so the day still shows it has a record).
     */
    fun dots(dayRecords: List<RecordWithCategory>): List<Emotion?> =
        dayRecords.sortedBy { it.record.createdAt }.take(MAX_DOTS).map { it.record.emotion }
}
