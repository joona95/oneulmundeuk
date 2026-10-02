package app.placeholder.journal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * TODO(font): Pretendard is the design font. Until the font files are added under res/font
 *  (Pretendard, SIL OFL), we fall back to the system sans-serif. Swap [AppFont] in one place.
 */
val AppFont: FontFamily = FontFamily.SansSerif

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = AppFont,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

/** Token names from the design system. */
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
}

internal val AppTypography = Typography(
    displaySmall = TypeTokens.display,
    titleLarge = TypeTokens.title,
    titleMedium = TypeTokens.heading,
    bodyLarge = TypeTokens.bodyLarge,
    bodyMedium = TypeTokens.body,
    bodySmall = TypeTokens.bodySmall,
    labelLarge = TypeTokens.label,
    labelMedium = TypeTokens.label,
    labelSmall = TypeTokens.caption,
)
