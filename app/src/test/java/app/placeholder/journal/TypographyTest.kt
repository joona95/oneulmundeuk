package app.placeholder.journal

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.placeholder.journal.ui.theme.TypeTokens
import app.placeholder.journal.ui.theme.appTypography
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the type scale: applying the app font must not change sizes, line heights or hierarchy. */
class TypographyTest {
    // Any family stands in for LINE Seed (res/font is not loaded in JVM unit tests).
    private val typography = appTypography(FontFamily.Serif)

    private fun pairs(t: Typography): List<Pair<TextStyle, TextStyle>> = listOf(
        t.displaySmall to TypeTokens.display,
        t.titleLarge to TypeTokens.title,
        t.titleMedium to TypeTokens.heading,
        t.bodyLarge to TypeTokens.bodyLarge,
        t.bodyMedium to TypeTokens.body,
        t.bodySmall to TypeTokens.bodySmall,
        t.labelLarge to TypeTokens.label,
        t.labelSmall to TypeTokens.caption,
    )

    @Test
    fun scaleIsUnchanged() {
        pairs(typography).forEach { (applied, token) ->
            assertEquals(token.fontSize, applied.fontSize)
            assertEquals(token.lineHeight, applied.lineHeight)
            assertEquals(token.letterSpacing, applied.letterSpacing)
        }
    }

    @Test
    fun onlyLabelWeightIsLightened() {
        assertEquals(FontWeight.SemiBold, TypeTokens.label.fontWeight)
        assertEquals(FontWeight.Medium, typography.labelLarge.fontWeight)
        // hierarchy kept: titles, headings and body keep their token weight
        assertEquals(TypeTokens.title.fontWeight, typography.titleLarge.fontWeight)
        assertEquals(TypeTokens.heading.fontWeight, typography.titleMedium.fontWeight)
        assertEquals(TypeTokens.display.fontWeight, typography.displaySmall.fontWeight)
        assertEquals(TypeTokens.bodyLarge.fontWeight, typography.bodyLarge.fontWeight)
    }

    @Test
    fun familyIsAppliedEverywhere() {
        pairs(typography).forEach { (applied, _) -> assertEquals(FontFamily.Serif, applied.fontFamily) }
        assertEquals(FontFamily.Serif, typography.labelMedium.fontFamily)
    }
}
