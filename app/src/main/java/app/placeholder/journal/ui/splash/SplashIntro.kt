package app.placeholder.journal.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.ui.components.EmotionMarker
import app.placeholder.journal.ui.components.rememberReduceMotion
import app.placeholder.journal.ui.theme.AppTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Brand name shown on the intro. Product name: 오늘문득 (package name is still a placeholder). */
const val BRAND_NAME = "오늘문득"

/**
 * Splash timeline (ms). Pure data so the "short, never makes you wait" budget is unit-tested.
 * drop → land (squash) → rebound (stretch) → settle → name → hold → fade out.
 */
object SplashMotion {
    const val DROP = 300
    const val SQUASH = 100
    const val REBOUND = 170
    const val SETTLE = 240 // spring; approximate
    const val HOLD = 260
    const val FADE_OUT = 220
    const val TOTAL = DROP + SQUASH + REBOUND + SETTLE + HOLD + FADE_OUT

    const val DROP_HEIGHT_DP = 64f
    const val SQUASH_X = 1.30f
    const val SQUASH_Y = 0.70f
    const val STRETCH_X = 0.90f
    const val STRETCH_Y = 1.15f
    const val REBOUND_LIFT_DP = 10f

    /** Reduce Motion: no movement, just a brief still frame of the mark and name. */
    const val REDUCED_HOLD = 450
}

/**
 * Cold-start brand intro, drawn over the app (the list composes and loads underneath meanwhile).
 * One jelly drops in, lands with a clear squash, rebounds once, settles; then the name appears.
 * Calm on purpose: one jelly, no particles, no gradients, no loops.
 */
@Composable
fun SplashIntro(onFinished: () -> Unit) {
    val t = AppTheme.tokens
    val reduce = rememberReduceMotion()
    val jellySize = 64.dp

    val y = remember { Animatable(if (reduce) 0f else -SplashMotion.DROP_HEIGHT_DP) } // dp, 0 = on the ground
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    val jellyAlpha = remember { Animatable(if (reduce) 1f else 0f) }
    val nameAlpha = remember { Animatable(if (reduce) 1f else 0f) }
    val nameRise = remember { Animatable(if (reduce) 0f else 6f) } // dp
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (reduce) {
            delay(SplashMotion.REDUCED_HOLD.toLong())
            overlayAlpha.snapTo(0f)
            onFinished()
            return@LaunchedEffect
        }
        coroutineScope {
            // 1. appear + drop (accelerating, slightly elongated while falling)
            launch { jellyAlpha.animateTo(1f, tween(140)) }
            launch { sx.animateTo(0.96f, tween(SplashMotion.DROP)) }
            launch { sy.animateTo(1.06f, tween(SplashMotion.DROP)) }
            y.animateTo(0f, tween(SplashMotion.DROP, easing = FastOutLinearInEasing))
        }
        coroutineScope {
            // 2. land: flat and wide
            launch { sx.animateTo(SplashMotion.SQUASH_X, tween(SplashMotion.SQUASH, easing = LinearOutSlowInEasing)) }
            sy.animateTo(SplashMotion.SQUASH_Y, tween(SplashMotion.SQUASH, easing = LinearOutSlowInEasing))
        }
        coroutineScope {
            // 3. rebound: narrow and tall, a small hop
            launch { sx.animateTo(SplashMotion.STRETCH_X, tween(SplashMotion.REBOUND, easing = FastOutSlowInEasing)) }
            launch { y.animateTo(-SplashMotion.REBOUND_LIFT_DP, tween(SplashMotion.REBOUND, easing = FastOutSlowInEasing)) }
            sy.animateTo(SplashMotion.STRETCH_Y, tween(SplashMotion.REBOUND, easing = FastOutSlowInEasing))
        }
        coroutineScope {
            // 4. settle (soft spring) while the name fades up
            val settle = spring<Float>(dampingRatio = 0.55f, stiffness = 420f)
            launch { sx.animateTo(1f, settle) }
            launch { sy.animateTo(1f, settle) }
            launch { y.animateTo(0f, settle) }
            launch { nameAlpha.animateTo(1f, tween(260)) }
            launch { nameRise.animateTo(0f, tween(260, easing = LinearOutSlowInEasing)) }
        }
        delay(SplashMotion.HOLD.toLong())
        overlayAlpha.animateTo(0f, tween(SplashMotion.FADE_OUT))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
            .background(MaterialTheme.colorScheme.background)
            // swallow taps during the ~1.3s intro
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .semantics { contentDescription = BRAND_NAME },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.align(BiasAlignment(0f, -0.15f)),
        ) {
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(jellySize + 8.dp)) {
                // ground shadow: widens as the jelly squashes, fades while it is in the air
                val air = (-y.value / SplashMotion.DROP_HEIGHT_DP).coerceIn(0f, 1f)
                val shadowColor = t.textPrimary.copy(alpha = 0.08f * (1f - air) * jellyAlpha.value)
                Canvas(Modifier.width(jellySize).height(8.dp)) {
                    val w = size.width * 0.7f * sx.value * (1f - 0.3f * air)
                    drawOval(shadowColor, topLeft = Offset((size.width - w) / 2, size.height * 0.25f), size = Size(w, size.height * 0.5f))
                }
                EmotionMarker(
                    emotion = Emotion.CALM,
                    size = jellySize,
                    modifier = Modifier
                        .offset { IntOffset(0, y.value.dp.toPx().roundToInt()) }
                        .graphicsLayer {
                            alpha = jellyAlpha.value
                            scaleX = sx.value
                            scaleY = sy.value
                            transformOrigin = TransformOrigin(0.5f, 0.9f) // squash against the ground
                        },
                )
            }
            Text(
                text = BRAND_NAME,
                style = MaterialTheme.typography.titleLarge,
                color = t.textPrimary,
                modifier = Modifier
                    .offset { IntOffset(0, nameRise.value.dp.roundToPx()) }
                    .alpha(nameAlpha.value),
            )
        }
    }
}
