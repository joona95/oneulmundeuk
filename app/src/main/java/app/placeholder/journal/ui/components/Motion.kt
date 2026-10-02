package app.placeholder.journal.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Motion principle: still by default, soft only when touched. No idle loops, no large bounce.

/** True when the system "Remove animations" setting is on — then we show state changes only. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Select / tap: squish → settle, once, each time [trigger] changes to a "selected" value. */
@Composable
fun Modifier.squishOn(trigger: Any?, active: Boolean): Modifier {
    val reduce = rememberReduceMotion()
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (!active || reduce) return@LaunchedEffect
        coroutineScope {
            launch { sx.animateTo(1.05f, tween(90)); sx.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
            launch { sy.animateTo(0.95f, tween(90)); sy.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
        }
    }
    return graphicsLayer { scaleX = sx.value; scaleY = sy.value }
}

/** Press: a soft give (0.98) while held. Pass the same interactionSource used by the clickable. */
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
