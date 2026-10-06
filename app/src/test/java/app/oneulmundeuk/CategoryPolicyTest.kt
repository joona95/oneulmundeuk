package app.oneulmundeuk

import app.oneulmundeuk.data.CategoryPolicy
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.db.DefaultCategories
import app.oneulmundeuk.data.db.RecordEntity
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.ui.explore.Explore
import app.oneulmundeuk.ui.explore.SuggestedQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Category archive policy: gone from new records, kept for the past (Records filter, Explore). */
class CategoryPolicyTest {
    private val work = CategoryEntity("w", "회사", 0, 0)
    private val daily = CategoryEntity("d", "일상", 1, 0)
    private val dev = CategoryEntity("dev", "개발", 2, 0, archivedAt = 100) // old category, deleted (archived)
    private val unused = CategoryEntity("old", "성장", 3, 0, archivedAt = 100) // archived, no record uses it
    private val all = listOf(work, daily, dev, unused)

    private fun rec(id: String, categoryId: String?, at: Long = 0) =
        RecordWithCategory(RecordEntity(id, "t-$id", at, at, null, categoryId, null), categoryName = null)

    @Test
    fun freshInstallSeedsFiveGeneralCategories() {
        assertEquals(listOf("회사", "일상", "취미", "관계", "기타"), DefaultCategories.names)
    }

    @Test
    fun createOffersOnlyActiveCategories() {
        assertEquals(listOf(work, daily), CategoryPolicy.editorOptions(all, selectedId = null))
        assertTrue(CategoryPolicy.selectableForNew(all, "w"))
        assertFalse(CategoryPolicy.selectableForNew(all, "dev")) // e.g. a restored draft pointing at it
        assertFalse(CategoryPolicy.selectableForNew(all, "missing"))
    }

    @Test
    fun editKeepsArchivedCurrentValueUntilChanged() {
        // the old record's current category stays visible and selected
        assertEquals(listOf(work, daily, dev), CategoryPolicy.editorOptions(all, selectedId = "dev"))
        // changed to an active one (or cleared) → the archived one is not offered again
        assertEquals(listOf(work, daily), CategoryPolicy.editorOptions(all, selectedId = "w"))
        assertEquals(listOf(work, daily), CategoryPolicy.editorOptions(all, selectedId = null))
    }

    @Test
    fun recordsFilterShowsArchivedOnlyWhileUsed() {
        val records = listOf(rec("r1", "dev"), rec("r2", "w"), rec("r3", null))
        assertEquals(listOf(work, daily, dev), CategoryPolicy.recordsFilterOptions(all, records)) // 성장: archived + unused → hidden
        assertEquals(listOf(work, daily), CategoryPolicy.recordsFilterOptions(all, listOf(rec("r2", "w"))))
    }

    @Test
    fun exploreKeepsArchivedCategoryHistory() {
        val day = 86_400_000L
        val records = listOf(rec("a", "dev", 0), rec("b", "dev", 10 * day), rec("c", "dev", 20 * day), rec("x", "w", 0))
        // topics: categories with records, archived included
        assertEquals(listOf(dev, work), Explore.frequentTopics(records, all))
        // data-based category suggestion may come from an archived category that past records use (unchanged rule)
        val qs = SuggestedQuestions.build(records, all, semanticAvailable = true, today = LocalDate.of(2026, 10, 7), zone = ZoneId.of("UTC"))
        assertTrue(qs.any { it.categoryId == "dev" && it.text == SuggestedQuestions.category("개발") })
    }

    @Test
    fun deleteDialogCopy() {
        assertEquals("'개발' 카테고리를 삭제할까요?", CategoryPolicy.deleteTitle("개발"))
        assertEquals("기존 기록의 카테고리는 그대로 유지돼요.\n새 기록에서는 더 이상 선택할 수 없어요.", CategoryPolicy.DELETE_BODY)
        assertEquals("취소" to "삭제", CategoryPolicy.DELETE_CANCEL to CategoryPolicy.DELETE_CONFIRM)
    }
}
