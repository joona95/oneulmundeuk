package app.oneulmundeuk

import app.oneulmundeuk.data.db.RecordEntity
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.ui.records.RecordsCalendar
import app.oneulmundeuk.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class RecordsCalendarTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = seoul) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private var seq = 0
    private fun rec(createdAt: Long, emotion: Emotion? = null, categoryId: String? = null) = RecordWithCategory(
        record = RecordEntity(
            id = "r${seq++}", text = "t", createdAt = createdAt, updatedAt = createdAt,
            emotion = emotion, categoryId = categoryId, photoPath = null,
        ),
        categoryName = categoryId,
    )

    // ── month grid ────────────────────────────────────────────────────────────

    @Test
    fun october2026StartsOnThursday() {
        val cells = RecordsCalendar.monthCells(YearMonth.of(2026, 10))
        assertEquals(0, cells.size % 7)
        assertEquals(35, cells.size) // 4 blanks + 31 days → 5 weeks
        assertTrue(cells.take(4).all { it == null }) // 일 월 화 수
        assertEquals(LocalDate.of(2026, 10, 1), cells[4]) // 목
        assertEquals(LocalDate.of(2026, 10, 31), cells[34]) // 토
    }

    @Test
    fun monthStartingOnSundayHasNoLeadingBlanks() {
        val cells = RecordsCalendar.monthCells(YearMonth.of(2026, 2)) // 2026-02-01 is a Sunday
        assertEquals(LocalDate.of(2026, 2, 1), cells[0])
        assertEquals(28, cells.size) // exactly 4 weeks, no trailing blanks
    }

    @Test
    fun sixWeekMonthAndLeapYear() {
        val aug = RecordsCalendar.monthCells(YearMonth.of(2026, 8)) // starts Saturday, 31 days
        assertEquals(42, aug.size)
        assertEquals(LocalDate.of(2026, 8, 1), aug[6])
        val feb2028 = RecordsCalendar.monthCells(YearMonth.of(2028, 2))
        assertEquals(29, feb2028.count { it != null })
    }

    // ── timestamp → LocalDate ────────────────────────────────────────────────

    @Test
    fun dayKeyUsesTheDeviceZoneAroundMidnight() {
        val justBefore = at(2026, 10, 3, 23, 59)
        val justAfter = at(2026, 10, 4, 0, 1)
        assertEquals(LocalDate.of(2026, 10, 3), TimeFormat.dayKey(justBefore, seoul))
        assertEquals(LocalDate.of(2026, 10, 4), TimeFormat.dayKey(justAfter, seoul))
        // the same instant is a different calendar day in UTC
        assertEquals(LocalDate.of(2026, 10, 3), TimeFormat.dayKey(justAfter, ZoneId.of("UTC")))
    }

    // ── grouping ──────────────────────────────────────────────────────────────

    @Test
    fun groupsByLocalDayOldestFirst() {
        val late = rec(at(2026, 10, 3, 23, 59))
        val early = rec(at(2026, 10, 3, 8, 0))
        val nextDay = rec(at(2026, 10, 4, 0, 1))
        val byDay = RecordsCalendar.groupByDay(listOf(nextDay, late, early), seoul) // DAO order: newest first
        assertEquals(listOf(early, late), byDay[LocalDate.of(2026, 10, 3)])
        assertEquals(listOf(nextDay), byDay[LocalDate.of(2026, 10, 4)])
        assertNull(byDay[LocalDate.of(2026, 10, 5)])
    }

    // ── dots ─────────────────────────────────────────────────────────────────

    @Test
    fun atMostThreeDotsInTimeOrder() {
        val day = listOf(
            rec(at(2026, 10, 3, 21, 0), Emotion.SAD),
            rec(at(2026, 10, 3, 9, 0), Emotion.CALM),
            rec(at(2026, 10, 3, 12, 0), null),
            rec(at(2026, 10, 3, 15, 0), Emotion.HAPPY),
        )
        val dots = RecordsCalendar.dots(day)
        assertEquals(RecordsCalendar.MAX_DOTS, dots.size)
        assertEquals(listOf(Emotion.CALM, null, Emotion.HAPPY), dots) // 4th (21:00) dropped; null = no emotion
    }

    @Test
    fun fewerRecordsFewerDots() {
        assertEquals(listOf(Emotion.TIRED), RecordsCalendar.dots(listOf(rec(at(2026, 10, 3, 9, 0), Emotion.TIRED))))
        assertTrue(RecordsCalendar.dots(emptyList()).isEmpty())
    }

    // ── category filter ──────────────────────────────────────────────────────

    @Test
    fun categoryFilterAppliesToListAndCalendarAlike() {
        val work = rec(at(2026, 10, 3, 9, 0), Emotion.CALM, "work")
        val life = rec(at(2026, 10, 3, 10, 0), Emotion.HAPPY, "life")
        val none = rec(at(2026, 10, 4, 10, 0), Emotion.SAD, null)
        val all = listOf(work, life, none)

        assertEquals(all, RecordsCalendar.filterByCategory(all, null)) // 전체
        val onlyWork = RecordsCalendar.filterByCategory(all, "work")
        assertEquals(listOf(work), onlyWork)

        // the calendar is built from the filtered list: only the "work" day keeps a dot
        val dots = RecordsCalendar.groupByDay(onlyWork, seoul).mapValues { RecordsCalendar.dots(it.value) }
        assertEquals(mapOf(LocalDate.of(2026, 10, 3) to listOf(Emotion.CALM)), dots)
    }

    @Test
    fun monthTitleAndDateHeader() {
        assertEquals("2026년 10월", TimeFormat.monthTitle(YearMonth.of(2026, 10)))
        assertEquals("2026년 10월 3일 토요일", TimeFormat.fullDate(LocalDate.of(2026, 10, 3)))
    }
}
