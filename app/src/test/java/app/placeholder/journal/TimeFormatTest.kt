package app.placeholder.journal

import app.placeholder.journal.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeFormatTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, seoul).toInstant().toEpochMilli()
    private val now = at(2026, 10, 2, 9, 0) // Friday

    @Test
    fun time() {
        assertEquals("오전 8:42", TimeFormat.time(at(2026, 10, 2, 8, 42), seoul))
        assertEquals("오후 12:30", TimeFormat.time(at(2026, 10, 2, 12, 30), seoul))
        assertEquals("오전 12:05", TimeFormat.time(at(2026, 10, 2, 0, 5), seoul))
        assertEquals("오후 11:40", TimeFormat.time(at(2026, 9, 24, 23, 40), seoul))
    }

    @Test
    fun fullDate() {
        assertEquals("2026년 9월 24일 목요일", TimeFormat.fullDate(at(2026, 9, 24, 23, 40), seoul))
    }

    @Test
    fun cardMeta() {
        assertEquals("오늘 · 오전 8:42", TimeFormat.cardMeta(at(2026, 10, 2, 8, 42), now, seoul))
        assertEquals("어제 · 오후 10:15", TimeFormat.cardMeta(at(2026, 10, 1, 22, 15), now, seoul))
        assertEquals("9월 24일 · 오후 11:40", TimeFormat.cardMeta(at(2026, 9, 24, 23, 40), now, seoul))
        assertEquals("2025년 10월 3일 · 오후 11:10", TimeFormat.cardMeta(at(2025, 10, 3, 23, 10), now, seoul))
    }

    @Test
    fun dayHeader() {
        assertEquals("오늘 · 10월 2일 금요일", TimeFormat.dayHeader(at(2026, 10, 2, 8, 42), now, seoul))
        assertEquals("어제 · 10월 1일 목요일", TimeFormat.dayHeader(at(2026, 10, 1, 22, 15), now, seoul))
        assertEquals("9월 30일 수요일", TimeFormat.dayHeader(at(2026, 9, 30, 7, 58), now, seoul))
        assertEquals("2025년 10월 3일 금요일", TimeFormat.dayHeader(at(2025, 10, 3, 23, 10), now, seoul))
    }

    @Test
    fun longDate() {
        assertEquals("2026년 4월 11일", TimeFormat.longDate(at(2026, 4, 11, 23, 40), seoul))
    }

    @Test
    fun ago() {
        // now = 2026-10-02
        assertEquals("오늘", TimeFormat.ago(at(2026, 10, 2, 0, 5), now, seoul))
        assertEquals("어제", TimeFormat.ago(at(2026, 10, 1, 23, 59), now, seoul))
        assertEquals("3일 전", TimeFormat.ago(at(2026, 9, 29, 9, 0), now, seoul))
        assertEquals("2주 전", TimeFormat.ago(at(2026, 9, 18, 9, 0), now, seoul))
        assertEquals("한 달 전", TimeFormat.ago(at(2026, 9, 2, 9, 0), now, seoul))
        assertEquals("6개월 전", TimeFormat.ago(at(2026, 4, 2, 9, 0), now, seoul))
        assertEquals("1년 전", TimeFormat.ago(at(2025, 10, 3, 9, 0), now, seoul)) // 364 days → not "11개월 전"
        assertEquals("1년 2개월 전", TimeFormat.ago(at(2025, 8, 2, 9, 0), now, seoul))
    }
}
