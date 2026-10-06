package app.oneulmundeuk

import app.oneulmundeuk.data.CategoryPolicy
import app.oneulmundeuk.data.NewCategoryName
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.settings.AppSettings
import app.oneulmundeuk.data.settings.RelatedThoughtsStatus
import app.oneulmundeuk.data.settings.relatedThoughtsStatus
import app.oneulmundeuk.ui.components.MarkerShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Settings MVP rules that need no Android runtime. */
class SettingsPolicyTest {
    @Test
    fun defaults() {
        val s = AppSettings()
        assertEquals(MarkerShape.Jelly, s.markerShape)
        assertFalse(s.reminderEnabled)
        assertFalse(s.relatedEnabled)
    }

    @Test
    fun markerShapesAndStoredKeys() {
        assertEquals(listOf("동글", "하트", "별", "둥근 사각", "조약돌", "다이아"), MarkerShape.entries.map { it.label })
        assertEquals(listOf("jelly", "heart", "star", "roundSquare", "pebble", "diamond"), MarkerShape.entries.map { it.key })
        MarkerShape.entries.forEach { assertEquals(it, MarkerShape.fromKey(it.key)) }
        assertEquals(MarkerShape.Jelly, MarkerShape.fromKey(null)) // nothing saved yet
        assertEquals(MarkerShape.Jelly, MarkerShape.fromKey("cloud")) // unknown (newer version) → default
    }

    @Test
    fun relatedStatus() {
        assertEquals(RelatedThoughtsStatus.OFF, relatedThoughtsStatus(enabled = false, modelInstalled = false))
        assertEquals(RelatedThoughtsStatus.OFF, relatedThoughtsStatus(enabled = false, modelInstalled = true)) // off ≠ delete
        assertEquals(RelatedThoughtsStatus.MODEL_NOT_DOWNLOADED, relatedThoughtsStatus(enabled = true, modelInstalled = false))
        assertEquals(RelatedThoughtsStatus.DOWNLOADING, relatedThoughtsStatus(enabled = true, modelInstalled = false, downloading = true))
        assertEquals(RelatedThoughtsStatus.READY, relatedThoughtsStatus(enabled = true, modelInstalled = true))
    }

    @Test
    fun newCategoryName() {
        val cats = listOf(CategoryEntity("w", "회사", 0, 0), CategoryEntity("dev", "개발", 1, 0, archivedAt = 5))
        assertEquals(NewCategoryName.Blank, CategoryPolicy.checkNewName("   ", cats))
        assertEquals(NewCategoryName.Ok("운동"), CategoryPolicy.checkNewName("  운동 ", cats)) // trimmed
        assertEquals(NewCategoryName.Duplicate, CategoryPolicy.checkNewName(" 회사", cats))
        assertEquals(NewCategoryName.UsedBefore, CategoryPolicy.checkNewName("개발", cats)) // archived name: refused, no restore
        assertEquals(listOf("회사"), CategoryPolicy.active(cats).map { it.name })
    }

    /** The shape setting reaches markers through the theme token; the Records calendar keeps plain color dots. */
    @Test
    fun shapeAppliesToMarkersButNotCalendar() {
        val marker = File("src/main/java/app/oneulmundeuk/ui/components/EmotionMarker.kt").readText()
        assertTrue(marker.contains("shape: MarkerShape = AppTheme.tokens.markerShape"))
        val calendar = File("src/main/java/app/oneulmundeuk/ui/records/RecordsCalendarView.kt").readText()
        assertFalse(calendar.contains("EmotionMarker"))
        assertFalse(calendar.contains("markerShape"))
    }
}
