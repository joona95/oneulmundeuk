package app.placeholder.journal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typography. The scale (sizes, line heights, hierarchy) is the Design Freeze scale.
 *
 * Font: LINE Seed Sans KR ([AppFonts.LineSeed]) with the small adjustments in [TypeTokens.forLineSeed].
 */
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

/** Token names from the design system (family-independent). */
object TypeTokens {
    val display = style(28, 36, FontWeight.Bold, -0.02)
    val title = style(22, 30, FontWeight.SemiBold, -0.015)
    val heading = style(17, 24, FontWeight.SemiBold, -0.01)
    val memory = style(17, 28, FontWeight.Normal, -0.005) // past records (Related / Rediscovery)
    val bodyLarge = style(17, 30, FontWeight.Normal, -0.005) // editor & detail body
    val body = style(15, 24, FontWeight.Normal, -0.003)
    val bodySmall = style(14, 21, FontWeight.Normal, -0.002)
    val label = style(13, 18, FontWeight.SemiBold, 0.0)
    val caption = style(12, 16, FontWeight.Medium, 0.0)

    /**
     * LINE Seed Sans KR ships Regular and Bold only (no Medium / SemiBold), so 600 renders as Bold.
     * Adjustments for this family:
     * - label 600 → 500 (= Regular): chips, buttons and day headers were too heavy in Bold at 13sp.
     *   Titles / headings keep their weight (→ Bold) to preserve the hierarchy.
     * - lineHeightStyle Center/Trim.None: LINE Seed's vertical metrics differ from the system font;
     *   centering inside the same line height keeps text optically centered in chips and buttons.
     * Sizes, line heights and letter spacing are unchanged.
     */
    fun forLineSeed(s: TextStyle, isLabel: Boolean = false): TextStyle = s.copy(
        fontWeight = if (isLabel) FontWeight.Medium else s.fontWeight,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/** Builds the Material typography for [family] (LINE Seed in the app; any family in unit tests). */
fun appTypography(family: FontFamily): Typography {
    fun f(s: TextStyle, isLabel: Boolean = false): TextStyle = TypeTokens.forLineSeed(s, isLabel).copy(fontFamily = family)
    return Typography(
        displaySmall = f(TypeTokens.display),
        titleLarge = f(TypeTokens.title),
        titleMedium = f(TypeTokens.heading),
        bodyLarge = f(TypeTokens.bodyLarge),
        bodyMedium = f(TypeTokens.body),
        bodySmall = f(TypeTokens.bodySmall),
        labelLarge = f(TypeTokens.label, isLabel = true),
        labelMedium = f(TypeTokens.label, isLabel = true),
        labelSmall = f(TypeTokens.caption),
    )
}
