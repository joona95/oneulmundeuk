package app.oneulmundeuk.ui.theme

import androidx.compose.ui.graphics.Color
import app.oneulmundeuk.data.model.Emotion

/** Emotion = Color. The same mapping is used for every marker shape and for calendar dots. */
data class EmotionColors(val fill: Color, val ink: Color)

fun Emotion.colors(): EmotionColors = when (this) {
    Emotion.CALM -> EmotionColors(Color(0xFF9CC9B4), Color(0xFF4E8A70)) // Mint
    Emotion.HAPPY -> EmotionColors(Color(0xFFF6C76B), Color(0xFFB98A25)) // Yellow
    Emotion.EXCITED -> EmotionColors(Color(0xFFF49A7E), Color(0xFFC25E42)) // Coral
    Emotion.SO_SO -> EmotionColors(Color(0xFFCFC9C0), Color(0xFF8C857A)) // Warm Gray
    Emotion.TIRED -> EmotionColors(Color(0xFFB7B3D9), Color(0xFF706BA6)) // Lavender
    Emotion.ANXIOUS -> EmotionColors(Color(0xFF8FB8DE), Color(0xFF4D7FAE)) // Light Blue
    Emotion.SAD -> EmotionColors(Color(0xFF7F9CC2), Color(0xFF48668F)) // Muted Blue
}
