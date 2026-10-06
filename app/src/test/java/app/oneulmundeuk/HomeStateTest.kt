package app.oneulmundeuk

import app.oneulmundeuk.data.db.RecordEntity
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.resurface.DateBasedResurfacedRecordSelector
import app.oneulmundeuk.ui.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Home empty state follows the current records only: 0 → empty, >0 → normal, deleted back to 0 → empty again. */
class HomeStateTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.of(2026, 10, 6)
    private val selector = DateBasedResurfacedRecordSelector()
    private val now = day.atStartOfDay(zone).toInstant().toEpochMilli() + 9 * 3_600_000L

    private fun rec(id: String) = RecordWithCategory(RecordEntity(id, "t-$id", now, now, null, null, null), categoryName = null)
    private fun state(records: List<RecordWithCategory>) = HomeViewModel.state(records, selector, day, zone)

    @Test
    fun initialNoRecordsShowsEmptyState() {
        val s = state(emptyList())
        assertFalse(s.loading)
        assertTrue(s.noRecords)
        assertTrue(s.recent.isEmpty())
    }

    @Test
    fun withRecordsShowsNormalHome() {
        val s = state(listOf(rec("a")))
        assertFalse(s.noRecords)
        assertEquals(listOf("a"), s.recent.map { it.record.id })
    }

    @Test
    fun deletingEveryRecordShowsTheSameEmptyStateAgain() {
        val emissions = listOf(emptyList(), listOf(rec("a")), emptyList<RecordWithCategory>()) // Room Flow: 0 → 1 → 0
        val states = emissions.map(::state)
        assertEquals(listOf(true, false, true), states.map { it.noRecords })
        assertEquals(states[0], states[2]) // first-run Home == Home after deleting everything
    }
}
