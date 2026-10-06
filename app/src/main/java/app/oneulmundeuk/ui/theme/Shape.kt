package app.oneulmundeuk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Soft & Clean radii (Figma `soft` variant): cards lg 16, hero surfaces / sheets xl 20, chips & buttons full. */
data class Radii(
    val sm: RoundedCornerShape = RoundedCornerShape(8.dp),
    val md: RoundedCornerShape = RoundedCornerShape(12.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(16.dp),
    val card: RoundedCornerShape = RoundedCornerShape(16.dp),
    val hero: RoundedCornerShape = RoundedCornerShape(20.dp),
    val sheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    val full: RoundedCornerShape = RoundedCornerShape(percent = 50),
)
