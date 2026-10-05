package app.placeholder.journal

import app.placeholder.journal.data.db.RecordEntity
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.ui.navigation.RelatedMemoriesRoute
import app.placeholder.journal.ui.related.buildRelatedMemoriesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedMemoriesTest {
    private fun rec(id: String, at: Long = 0L) = RecordWithCategory(RecordEntity(id, "t-$id", at, at, null, null, null), categoryName = null)

    @Test
    fun screenStateFollowsHandedOverOrder() {
        val all = listOf(rec("now", 100), rec("old", 1), rec("mid", 50))
        val s = buildRelatedMemoriesState(all, "now", listOf("mid", "old"))
        assertFalse(s.loading)
        assertEquals("now", s.current?.record?.id)
        assertEquals(listOf("mid", "old"), s.past.map { it.record.id })
    }

    @Test
    fun deletedRecordsAndSelfAreSkipped() {
        val all = listOf(rec("now"), rec("a"))
        val s = buildRelatedMemoriesState(all, "now", listOf("gone", "a", "now", "a"))
        assertEquals(listOf("a"), s.past.map { it.record.id })
        assertNull(buildRelatedMemoriesState(emptyList(), "now", listOf("a")).current)
    }

    @Test
    fun routeRoundTripsIdsInOrder() {
        val ids = listOf("3f2a-1", "0b9c-2", "aa11-3")
        val route = RelatedMemoriesRoute.of("now", ids)
        assertEquals(ids, route.relatedIdList)
        assertTrue(RelatedMemoriesRoute("now", "").relatedIdList.isEmpty())
    }
}
