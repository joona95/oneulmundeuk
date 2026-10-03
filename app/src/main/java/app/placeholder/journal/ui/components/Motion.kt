package app.placeholder.journal.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Motion principle: the UI is still by default; only the jelly is soft — and only when touched.
// No idle loops, no sway, no breathing.

/**
 * True when the system "Remove animations" setting is on (developer options / accessibility).
 * Callers then show the final state only. Every motion in the app goes through this one check.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The selection squash & stretch curve lives in [JellyCurve] (pure data, unit-tested).
 * This object turns it into Compose animation specs.
 */
object JellyMotion {
    /** Pivot slightly below center: the jelly feels pressed onto a surface, but stays inside its halo. */
    val pivot = TransformOrigin(0.5f, 0.62f)

    internal fun spec(
        curve: List<JellyPose> = JellyCurve.select,
        axis: (JellyPose) -> Float,
    ): AnimationSpec<Float> = keyframes {
        val poses = curve
        durationMillis = poses.last().atMillis
        // `using` sets the easing of the segment that STARTS at that pose:
        // snappy into the squash and the rebound, then a decelerating settle.
        poses.dropLast(1).forEachIndexed { i, p ->
            val easing = if (i <= 1) FastOutSlowInEasing else LinearOutSlowInEasing
            axis(p) at p.atMillis using easing
        }
    }
}

/**
 * Squash & stretch once each time [key] changes while [play] is true. Not on first composition, so
 * opening a record that already has an emotion does not bounce. Reduce Motion → no animation.
 */
@Composable
fun Modifier.jellySquash(key: Any?, play: Boolean): Modifier {
    val reduce = rememberReduceMotion()
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) { first = false; return@LaunchedEffect }
        if (!play || reduce) return@LaunchedEffect
        sx.snapTo(1f); sy.snapTo(1f)
        coroutineScope {
            launch { sx.animateTo(1f, JellyMotion.spec { it.scaleX }) }
            launch { sy.animateTo(1f, JellyMotion.spec { it.scaleY }) }
        }
    }
    return graphicsLayer {
        scaleX = sx.value
        scaleY = sy.value
        transformOrigin = JellyMotion.pivot
    }
}

/** Press: a soft give (0.98) while held — cards and buttons. Pass the clickable's interactionSource. */
@Composable
fun Modifier.softGive(interactionSource: MutableInteractionSource): Modifier {
    val reduce = rememberReduceMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.98f else 1f,
        animationSpec = tween(if (pressed) 100 else 180),
        label = "softGive",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Jelly marker press: squashes a little while the finger is down (before the selection squash plays). */
@Composable
fun Modifier.jellyPress(interactionSource: MutableInteractionSource): Modifier {
    val reduce = rememberReduceMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val sx by animateFloatAsState(if (pressed && !reduce) 1.06f else 1f, tween(if (pressed) 80 else 160), label = "pressX")
    val sy by animateFloatAsState(if (pressed && !reduce) 0.92f else 1f, tween(if (pressed) 80 else 160), label = "pressY")
    return graphicsLayer { scaleX = sx; scaleY = sy; transformOrigin = JellyMotion.pivot }
}
