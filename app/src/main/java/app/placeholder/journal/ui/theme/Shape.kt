package app.placeholder.journal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Soft & Clean radii: cards 20, hero surfaces / sheets 24, pills full. */
data class Radii(
    val sm: RoundedCornerShape = RoundedCornerShape(8.dp),
    val md: RoundedCornerShape = RoundedCornerShape(12.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(16.dp),
    val card: RoundedCornerShape = RoundedCornerShape(20.dp),
    val hero: RoundedCornerShape = RoundedCornerShape(24.dp),
    val sheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    val full: RoundedCornerShape = RoundedCornerShape(percent = 50),
)
