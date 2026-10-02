package app.placeholder.journal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.ui.theme.AppTheme
import app.placeholder.journal.ui.theme.colors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * "내 감정 조각" shapes. Emotion = Color, Shape = user preference: every emotion uses the same chosen
 * shape and only the color changes. All shapes share one jelly treatment (slightly irregular silhouette,
 * soft edge, tiny highlight, normalized visual weight). Ported 1:1 from the Figma UI Builder (markers.ts).
 */
enum class MarkerShape(val label: String, internal val seed: Float) {
    Jelly("동글동글", 0.6f),
    Heart("하트", 1.4f),
    Star("별", 2.1f),
    Square("네모", 0.9f),
    Pebble("조약돌", 0.6f),
    Diamond("마름모", 1.7f),
}

/** A small jelly marker filled with the emotion's color. Pair it with the label wherever meaning matters. */
@Composable
fun EmotionMarker(
    emotion: Emotion,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: MarkerShape = AppTheme.tokens.markerShape,
) {
    val path = remember(shape) { JellyGeometry.path(shape) }
    val highlight = remember { JellyGeometry.highlight() }
    val c = emotion.colors()
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / JellyGeometry.VIEWPORT, pivot = Offset.Zero) {
            drawPath(path, c.fill)
            drawPath(path, c.ink.copy(alpha = 0.18f), style = Stroke(width = 0.8f))
            drawPath(highlight, Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
        }
    }
}

/** Geometry on a 24×24 viewport. Deterministic: the same shape always produces the same outline. */
internal object JellyGeometry {
    const val VIEWPORT = 24f
    private const val TAU = (2 * Math.PI).toFloat()
    private const val UP = (-Math.PI / 2).toFloat()

    private fun outline(shape: MarkerShape, n: Int = 72): List<Offset> = List(n) { i ->
        val th = i.toFloat() / n * TAU
        val c = cos(th)
        val s = sin(th)
        when (shape) {
            MarkerShape.Jelly -> Offset(c, s)
            MarkerShape.Square -> {
                val r = 1f / (abs(c).pow(4) + abs(s).pow(4)).pow(0.25f)
                Offset(c * r, s * r)
            }
            MarkerShape.Diamond -> {
                val e = 1.22f
                val r = 1f / (abs(c).pow(e) + abs(s).pow(e)).pow(1f / e)
                Offset(c * r * 0.88f, s * r)
            }
            MarkerShape.Star -> {
                val r = 1f + 0.26f * cos(5 * (th - UP))
                Offset(c * r, s * r)
            }
            MarkerShape.Pebble -> {
                val rr = 1f + 0.05f * cos(3 * th + 1.2f)
                Offset(c * rr * 1.18f, s * rr * 0.8f + 0.06f * c * c)
            }
            MarkerShape.Heart -> {
                // parametric heart, sampled by parameter
                val x = 16f * sin(th).pow(3)
                val y = -(13f * cos(th) - 5f * cos(2 * th) - 2f * cos(3 * th) - cos(4 * th))
                Offset(x / 16f, y / 16f)
            }
        }
    }

    fun path(shape: MarkerShape): Path {
        val raw = outline(shape)
        val cx = raw.sumOf { it.x.toDouble() }.toFloat() / raw.size
        val cy = raw.sumOf { it.y.toDouble() }.toFloat() / raw.size
        // gentle deterministic wobble — no edge is perfectly geometric
        val wob = raw.map { p ->
            val a = atan2(p.y - cy, p.x - cx)
            val k = 1f + 0.022f * sin(2 * a + shape.seed) + 0.016f * sin(3 * a + shape.seed * 1.7f)
            Offset(cx + (p.x - cx) * k, cy + (p.y - cy) * k)
        }
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        wob.forEach { x0 = min(x0, it.x); x1 = max(x1, it.x); y0 = min(y0, it.y); y1 = max(y1, it.y) }
        val target = when (shape) {
            MarkerShape.Star, MarkerShape.Diamond, MarkerShape.Pebble -> 19f
            MarkerShape.Heart -> 18f
            else -> 17f
        }
        val scale = target / max(x1 - x0, y1 - y0)
        val mx = (x0 + x1) / 2
        val my = (y0 + y1) / 2
        val pts = wob.map { Offset(12f + (it.x - mx) * scale, 12.2f + (it.y - my) * scale) }
        return smooth(pts)
    }

    /** Closed Catmull-Rom → cubic Bézier. */
    private fun smooth(pts: List<Offset>): Path {
        val n = pts.size
        fun at(i: Int) = pts[(i + n) % n]
        return Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until n) {
                val p0 = at(i - 1); val p1 = at(i); val p2 = at(i + 1); val p3 = at(i + 2)
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
                    p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
                    p2.x, p2.y,
                )
            }
            close()
        }
    }

    fun highlight(): Path = Path().apply {
        moveTo(7.6f, 10.4f)
        quadraticBezierTo(8.3f, 8.4f, 10.3f, 7.8f)
    }
}
