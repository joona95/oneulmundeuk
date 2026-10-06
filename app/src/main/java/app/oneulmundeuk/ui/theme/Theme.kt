package app.oneulmundeuk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.oneulmundeuk.ui.components.MarkerShape

/** Values Material 3 has no slot for. Read with `AppTheme.tokens`. */
@Immutable
data class AppTokens(
    val spacing: Spacing = Spacing(),
    val sizes: Sizes = Sizes(),
    val radii: Radii = Radii(),
    val textPrimary: Color = Palette.TextPrimary,
    val textSecondary: Color = Palette.TextSecondary,
    val textTertiary: Color = Palette.TextTertiary,
    val surfaceSecondary: Color = Palette.SurfaceSecondary,
    val border: Color = Palette.Border,
    val borderStrong: Color = Palette.BorderStrong,
    /**
     * "내 감정 조각" — the user's one shape for every emotion marker (Settings, DataStore `settings.marker_shape`),
     * provided from MainActivity. Default 동글. Read by [app.oneulmundeuk.ui.components.EmotionMarker] only —
     * the Records calendar keeps plain color dots.
     */
    val markerShape: MarkerShape = MarkerShape.Jelly,
)

private val LocalAppTokens = staticCompositionLocalOf { AppTokens() }

private val LightColors = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.AccentContainer,
    onPrimaryContainer = Palette.OnAccentContainer,
    secondary = Palette.TextPrimary, // selection states are charcoal, not Sage
    onSecondary = Palette.Background,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceSecondary,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    outline = Palette.BorderStrong,
    outlineVariant = Palette.Border,
    error = Color(0xFFB3261E),
)

private val AppTypography = appTypography(AppFonts.LineSeed)

/** Light only for this milestone (the design is Warm Ivory). Dark theme is a later decision. */
@Composable
fun AppTheme(tokens: AppTokens = AppTokens(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppTokens provides tokens) {
        MaterialTheme(colorScheme = LightColors, typography = AppTypography, content = content)
    }
}

object AppTheme {
    val tokens: AppTokens
        @Composable get() = LocalAppTokens.current
}
