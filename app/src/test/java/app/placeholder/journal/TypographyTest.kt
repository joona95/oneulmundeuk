package app.placeholder.journal

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.placeholder.journal.ui.theme.appTypography
import org.junit.Assert.assertEquals
import org.junit.Test

/** Font experiment guard: swapping the family must not change the Design Freeze type scale. */
class TypographyTest {
    private val system = appTypography(FontFamily.SansSerif, lineSeed = false)
    private val lineSeed = appTypography(FontFamily.Serif, lineSeed = true) // any family stands in for LINE Seed

    private fun styles(t: androidx.compose.material3.Typography) = listOf(
        t.displaySmall, t.titleLarge, t.titleMedium, t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelSmall,
    )

    @Test
    fun scaleIsUnchanged() {
        styles(system).zip(styles(lineSeed)).forEach { (a, b) ->
            assertEquals(a.fontSize, b.fontSize)
            assertEquals(a.lineHeight, b.lineHeight)
            assertEquals(a.letterSpacing, b.letterSpacing)
        }
    }

    @Test
    fun onlyLabelWeightIsLightenedForLineSeed() {
        assertEquals(FontWeight.SemiBold, system.labelLarge.fontWeight)
        assertEquals(FontWeight.Medium, lineSeed.labelLarge.fontWeight)
        // hierarchy kept: titles and headings stay heavy
        assertEquals(system.titleLarge.fontWeight, lineSeed.titleLarge.fontWeight)
        assertEquals(system.titleMedium.fontWeight, lineSeed.titleMedium.fontWeight)
        assertEquals(system.displaySmall.fontWeight, lineSeed.displaySmall.fontWeight)
        assertEquals(system.bodyLarge.fontWeight, lineSeed.bodyLarge.fontWeight)
    }

    @Test
    fun familyIsAppliedEverywhere() {
        styles(system).forEach { assertEquals(FontFamily.SansSerif, it.fontFamily) }
        styles(lineSeed).forEach { assertEquals(FontFamily.Serif, it.fontFamily) }
    }
}
