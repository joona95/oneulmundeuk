package app.placeholder.journal.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Korean date/time strings used across screens. Pure functions: `now` and `zone` are injectable for tests. */
object TimeFormat {
    private fun at(epochMillis: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)

    private fun weekday(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.KOREAN) // "목요일"

    /** "오전 8:42", "오후 11:40", "오후 12:30" */
    fun time(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = at(epochMillis, zone)
        val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "${if (t.hour < 12) "오전" else "오후"} $h12:${"%02d".format(t.minute)}"
    }

    /** "2026년 9월 24일 목요일" */
    fun fullDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        fullDate(dayKey(epochMillis, zone))

    /** "2026년 9월 24일 목요일" — for a calendar date (no time zone involved). */
    fun fullDate(date: LocalDate): String =
        "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 ${weekday(date.dayOfWeek)}"

    /** Calendar header: "2026년 10월" */
    fun monthTitle(month: YearMonth): String = "${month.year}년 ${month.monthValue}월"

    private fun daysAgo(epochMillis: Long, now: Long, zone: ZoneId): Long {
        val day = at(epochMillis, zone).toLocalDate()
        val today = at(now, zone).toLocalDate()
        return ChronoUnit.DAYS.between(day, today)
    }

    private fun monthDay(d: LocalDate, today: LocalDate, withWeekday: Boolean): String {
        val year = if (d.year != today.year) "${d.year}년 " else ""
        return "$year${d.monthValue}월 ${d.dayOfMonth}일" + if (withWeekday) " ${weekday(d.dayOfWeek)}" else ""
    }

    /** List group header: "오늘 · 10월 2일 금요일", "어제 · 10월 1일 목요일", "9월 30일 수요일", "2025년 10월 3일 금요일". */
    fun dayHeader(epochMillis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val d = at(epochMillis, zone).toLocalDate()
        val today = at(now, zone).toLocalDate()
        val body = monthDay(d, today, withWeekday = true)
        return when (daysAgo(epochMillis, now, zone)) {
            0L -> "오늘 · $body"
            1L -> "어제 · $body"
            else -> body
        }
    }

    /** Card meta: "오늘 · 오전 8:42", "어제 · 오후 10:15", "9월 24일 · 오후 11:40". */
    fun cardMeta(epochMillis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val head = when (daysAgo(epochMillis, now, zone)) {
            0L -> "오늘"
            1L -> "어제"
            else -> monthDay(at(epochMillis, zone).toLocalDate(), at(now, zone).toLocalDate(), withWeekday = false)
        }
        return "$head · ${time(epochMillis, zone)}"
    }

    /**
     * The ONE timestamp → calendar-day rule (device time zone). The list groups, the calendar dots and the
     * selected-day records all use it, so a record near midnight lands on the same day everywhere.
     */
    fun dayKey(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = at(epochMillis, zone).toLocalDate()
}
