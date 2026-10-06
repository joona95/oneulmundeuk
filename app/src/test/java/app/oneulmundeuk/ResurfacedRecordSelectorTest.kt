package app.oneulmundeuk

import app.oneulmundeuk.data.db.RecordEntity
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.resurface.DateBasedResurfacedRecordSelector
import app.oneulmundeuk.resurface.ResurfacePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ResurfacedRecordSelectorTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 4) // 1개월 전 = 9/4, 3개월 전 = 7/4, 1년 전 = 2025-10-04
    private val selector = DateBasedResurfacedRecordSelector()

    private fun at(date: LocalDate, h: Int = 9, min: Int = 0, zone: ZoneId = seoul) =
        ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun rec(id: String, createdAt: Long) = RecordWithCategory(
        record = RecordEntity(id, "t", createdAt, createdAt, null, null, null),
        categoryName = null,
    )

    private fun rec(id: String, date: LocalDate, h: Int = 9) = rec(id, at(date, h))

    private fun pick(vararg records: RecordWithCategory, zone: ZoneId = seoul) =
        selector.select(records.toList(), today, zone)

    @Test
    fun recentRecordsAreNeverResurfaced() {
        // A wide window would reach them — the 14-day minimum age still excludes them.
        val wide = DateBasedResurfacedRecordSelector(minAgeDays = 14, windowDays = 20)
        val tenDaysAgo = rec("a", today.minusDays(10))
        assertNull(wide.select(listOf(tenDaysAgo), today, seoul))
        val fourteenDaysAgo = rec("b", today.minusDays(14))
        assertEquals("b", wide.select(listOf(tenDaysAgo, fourteenDaysAgo), today, seoul)?.item?.record?.id)
        // with the default window, something written today never comes back
        assertNull(pick(rec("c", today)))
    }

    @Test
    fun oneMonthCandidate() {
        val r = pick(rec("m1", LocalDate.of(2026, 9, 6)))
        assertEquals(ResurfacePeriod.OneMonth, r?.period)
        assertEquals("m1", r?.item?.record?.id)
    }

    @Test
    fun threeMonthCandidate() {
        val r = pick(rec("m3", LocalDate.of(2026, 7, 1)))
        assertEquals(ResurfacePeriod.ThreeMonths, r?.period)
    }

    @Test
    fun oneYearCandidate() {
        val r = pick(rec("y1", LocalDate.of(2025, 10, 10)))
        assertEquals(ResurfacePeriod.OneYear, r?.period)
    }

    @Test
    fun priorityIsOneYearThenThreeMonthsThenOneMonth() {
        val m1 = rec("m1", LocalDate.of(2026, 9, 4))
        val m3 = rec("m3", LocalDate.of(2026, 7, 4))
        val y1 = rec("y1", LocalDate.of(2025, 10, 4))
        assertEquals("y1", pick(m1, m3, y1)?.item?.record?.id)
        assertEquals("m3", pick(m1, m3)?.item?.record?.id)
        assertEquals("m1", pick(m1)?.item?.record?.id)
    }

    @Test
    fun windowIsPlusMinusSevenDays() {
        // 3개월 전 target = 7/4
        assertEquals(ResurfacePeriod.ThreeMonths, pick(rec("in-early", LocalDate.of(2026, 6, 27)))?.period)
        assertEquals(ResurfacePeriod.ThreeMonths, pick(rec("in-late", LocalDate.of(2026, 7, 11)))?.period)
        assertNull(pick(rec("out-early", LocalDate.of(2026, 6, 26))))
        assertNull(pick(rec("out-late", LocalDate.of(2026, 7, 12))))
    }

    @Test
    fun closestToTargetWins() {
        val threeAway = rec("far", LocalDate.of(2026, 9, 1))
        val twoAway = rec("near", LocalDate.of(2026, 9, 6))
        assertEquals("near", pick(threeAway, twoAway)?.item?.record?.id)
    }

    @Test
    fun tiesAreDeterministic() {
        // same distance (2 days before / after 9/4) → the older record
        val before = rec("before", LocalDate.of(2026, 9, 2))
        val after = rec("after", LocalDate.of(2026, 9, 6))
        assertEquals("before", pick(after, before)?.item?.record?.id)
        // same day → earlier time; same instant → smaller id; input order never matters
        val morning = rec("z-morning", LocalDate.of(2026, 9, 4), h = 8)
        val evening = rec("a-evening", LocalDate.of(2026, 9, 4), h = 21)
        assertEquals("z-morning", pick(evening, morning)?.item?.record?.id)
        val sameA = rec("a", LocalDate.of(2026, 9, 4))
        val sameB = rec("b", LocalDate.of(2026, 9, 4))
        assertEquals("a", pick(sameB, sameA)?.item?.record?.id)
        assertEquals(pick(sameA, sameB), pick(sameB, sameA))
    }

    @Test
    fun noCandidateGivesNull() {
        assertNull(pick())
        // 6 weeks ago is between the 1-month and 3-month windows
        assertNull(pick(rec("gap", today.minusWeeks(6))))
    }

    @Test
    fun localDateFollowsTheTimeZone() {
        val exact = DateBasedResurfacedRecordSelector(windowDays = 0)
        // 2026-07-04 23:30 UTC is 2026-07-05 08:30 in Seoul
        val record = rec("tz", at(LocalDate.of(2026, 7, 4), 23, 30, ZoneId.of("UTC")))
        assertEquals(ResurfacePeriod.ThreeMonths, exact.select(listOf(record), today, ZoneId.of("UTC"))?.period)
        assertNull(exact.select(listOf(record), today, seoul))
    }

    @Test
    fun labelsAreSoft() {
        assertEquals(listOf("1년 전쯤", "3개월 전쯤", "1개월 전쯤"), ResurfacePeriod.entries.map { it.label })
        assertTrue(ResurfacePeriod.entries.none { it.label.contains("일 전") })
    }
}
