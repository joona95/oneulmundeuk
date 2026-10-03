package app.placeholder.journal.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.placeholder.journal.R

/**
 * App font: LINE Seed Sans KR (SIL OFL 1.1, © LY Corporation — see docs/font.md).
 * Files live in `res/font/` and are referenced through `R.font`, so a missing file is a build error.
 */
object AppFonts {
    /**
     * The family ships Regular and Bold only. Every weight the app uses is mapped explicitly so nothing
     * is synthesized (no faux bold): 400/500 → Regular, 600/700 → Bold.
     */
    val LineSeed: FontFamily = FontFamily(
        Font(R.font.line_seed_kr_regular, FontWeight.Normal),
        Font(R.font.line_seed_kr_regular, FontWeight.Medium),
        Font(R.font.line_seed_kr_bold, FontWeight.SemiBold),
        Font(R.font.line_seed_kr_bold, FontWeight.Bold),
    )
}
