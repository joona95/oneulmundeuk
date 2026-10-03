package app.placeholder.journal.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing / size tokens (dp = Figma px at 360 width). */
data class Spacing(
    val hair: Dp = 2.dp,
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,
    // Semantic (Comfortable density)
    val screenPadding: Dp = 20.dp,
    val sectionGap: Dp = 40.dp,
    val cardPadding: Dp = 20.dp,
    val listGap: Dp = 12.dp,
)

data class Sizes(
    val markerXs: Dp = 14.dp, // list / timeline marker
    val markerSm: Dp = 20.dp, // inline (detail pill)
    val markerMd: Dp = 28.dp, // picker
    val markerLg: Dp = 32.dp, // selected marker in the picker
    val chipMinHeight: Dp = 32.dp,
    val buttonMinHeight: Dp = 48.dp,
    val fab: Dp = 56.dp,
    val iconButton: Dp = 40.dp,
    val appBar: Dp = 56.dp,
    val categoryTagMax: Dp = 120.dp,
    val hairline: Dp = 1.dp,
)
