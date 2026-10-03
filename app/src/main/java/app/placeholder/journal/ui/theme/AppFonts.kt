package app.placeholder.journal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.placeholder.journal.R

/**
 * FONT EXPERIMENT — LINE Seed Sans KR (not a final decision; see docs/font-experiment.md).
 *
 * The font files live in `res/font/` and are referenced through `R.font`, so a missing or renamed file
 * is a build error instead of a silent fallback. Set [USE_LINE_SEED] to false to compare against the
 * system font (Android default: Noto Sans CJK KR for Hangul) without removing the files.
 */
object AppFonts {
    const val USE_LINE_SEED = true

    /**
     * LINE Seed Sans KR ships Regular and Bold only. Every weight the app uses is mapped explicitly,
     * so nothing is synthesized (no faux bold): 400/500 → Regular, 600/700 → Bold.
     */
    val LineSeed: FontFamily = FontFamily(
        Font(R.font.line_seed_kr_regular, FontWeight.Normal),
        Font(R.font.line_seed_kr_regular, FontWeight.Medium),
        Font(R.font.line_seed_kr_bold, FontWeight.SemiBold),
        Font(R.font.line_seed_kr_bold, FontWeight.Bold),
    )

    @Immutable
    data class Resolved(val family: FontFamily, val isLineSeed: Boolean)

    val resolved: Resolved =
        if (USE_LINE_SEED) Resolved(LineSeed, isLineSeed = true) else Resolved(FontFamily.SansSerif, isLineSeed = false)
}

@Composable
internal fun rememberAppFonts(): AppFonts.Resolved = AppFonts.resolved
