package app.oneulmundeuk.util

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

    /** Compact date: "2026. 7. 5" */
    fun dotDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dayKey(epochMillis, zone).let { "${it.year}. ${it.monthValue}. ${it.dayOfMonth}" }

    /** Related Memories date: "2026년 4월 11일" (no weekday). */
    fun longDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dayKey(epochMillis, zone).let { "${it.year}년 ${it.monthValue}월 ${it.dayOfMonth}일" }

    /**
     * Related Memories time cue (Design Freeze Thread B, same rule as the Figma builder's fmtAgo):
     * "오늘", "어제", "3일 전", "2주 전", "한 달 전", "6개월 전", "1년 전", "1년 2개월 전".
     * Months are rounded (≈30.44 days) so 364 days reads "1년 전", not "11개월 전".
     */
    fun ago(epochMillis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val days = daysAgo(epochMillis, now, zone)
        if (days <= 0) return "오늘"
        if (days == 1L) return "어제"
        if (days < 7) return "${days}일 전"
        if (days < 28) return "${Math.round(days / 7.0)}주 전"
        val months = Math.round(days / 30.44).toInt()
        if (months <= 1) return "한 달 전"
        if (months < 12) return "${months}개월 전"
        val years = months / 12
        val rest = months % 12
        return if (rest == 0) "${years}년 전" else "${years}년 ${rest}개월 전"
    }

    /** Home date line: "10월 4일 일요일" */
    fun monthDayWeekday(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일 ${weekday(date.dayOfWeek)}"

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
