package app.placeholder.journal.ui.theme

import android.content.res.AssetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * FONT EXPERIMENT — LINE Seed Sans KR (not a final decision; see docs/font-experiment.md).
 *
 * The font files are loaded from `app/src/main/assets/fonts/` when present. If they are missing, the app
 * falls back to the system sans-serif, so the project always builds and runs. Set [USE_LINE_SEED] to
 * false to compare against the system font without removing the files.
 */
object AppFonts {
    const val USE_LINE_SEED = true

    const val DIR = "fonts"
    const val REGULAR = "LINESeedKR-Rg.ttf" // official file name from LINE_Seed_Sans_KR.zip
    const val BOLD = "LINESeedKR-Bd.ttf"

    @Immutable
    data class Resolved(val family: FontFamily, val isLineSeed: Boolean)

    private val System = Resolved(FontFamily.SansSerif, isLineSeed = false)

    @OptIn(ExperimentalTextApi::class) // harmless if the asset Font() overload is already stable
    fun resolve(assets: AssetManager): Resolved {
        if (!USE_LINE_SEED) return System
        val present = runCatching { assets.list(DIR)?.toSet().orEmpty() }.getOrDefault(emptySet())
        if (REGULAR !in present || BOLD !in present) return System
        // The family ships Regular + Bold only. Map every weight we use explicitly so nothing is
        // synthesized (no faux bold): 400/500 → Regular, 600/700 → Bold.
        val family = FontFamily(
            Font(path = "$DIR/$REGULAR", assetManager = assets, weight = FontWeight.Normal),
            Font(path = "$DIR/$REGULAR", assetManager = assets, weight = FontWeight.Medium),
            Font(path = "$DIR/$BOLD", assetManager = assets, weight = FontWeight.SemiBold),
            Font(path = "$DIR/$BOLD", assetManager = assets, weight = FontWeight.Bold),
        )
        return Resolved(family, isLineSeed = true)
    }
}

@Composable
internal fun rememberAppFonts(): AppFonts.Resolved {
    val context = LocalContext.current
    return remember { AppFonts.resolve(context.assets) }
}
