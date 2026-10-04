package app.placeholder.journal.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What was saved. The first record ever gets a slightly longer, warmer message; the motion is the same. */
enum class SaveFeedbackKind { Saved, FirstRecord }

object SaveFeedbackCopy {
    const val SAVED = "저장했어요"
    const val FIRST_TITLE = "첫 생각을 남겼어요."
    const val FIRST_BODY = "오늘의 생각이 언젠가\n다시 문득 떠오를 거예요."
}

/** Timeline (ms). After the jelly settles the message stays briefly so the save registers; the first-record copy stays longer to be read. */
object SaveFeedbackTiming {
    val MOTION = JellyCurve.saveSuccess.last().atMillis // 600
    const val SCRIM_IN = 120
    const val TEXT_DELAY = 120
    const val TEXT_IN = 180
    const val HOLD_SAVED = 350 // 600ms jelly + 350ms still "저장했어요" ≈ 0.95s
    const val HOLD_RELATED = 500 // 600ms jelly + 500ms "저장했어요", then Related Memories ≈ 1.1s
    const val HOLD_FIRST = 1100
    const val REDUCED_SAVED = 800
    const val REDUCED_FIRST = 1500
}

/**
 * Save-success feedback: a lightweight overlay over the current screen with the record's emotion jelly
 * doing one "통!" (pop-in → squash → stretch → settle), and a short message. Shown only AFTER the save
 * has succeeded — it never delays the database write. Calls [onFinished] once; the caller then continues
 * its normal navigation. Tapping skips the remaining hold.
 *
 * No emotion selected → the neutral (그냥 그래) jelly color is used as the plain mark.
 *
 * [toRelated] is asked when the jelly finishes: true → Related Memories follows, so "저장했어요" holds a little
 * longer ([SaveFeedbackTiming.HOLD_RELATED]) before moving on. It may suspend until that is known (no spinner).
 * Reduce Motion keeps its usual short static frame either way.
 */
@Composable
fun SaveSuccessOverlay(
    emotion: Emotion?,
    kind: SaveFeedbackKind,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    toRelated: suspend () -> Boolean = { false },
) {
    val t = AppTheme.tokens
    val reduce = rememberReduceMotion()
    val finish by rememberUpdatedState(onFinished)
    var done by remember { mutableStateOf(false) }
    val complete: () -> Unit = { if (!done) { done = true; finish() } }

    val scrim = remember { Animatable(if (reduce) 1f else 0f) }
    val jellyAlpha = remember { Animatable(if (reduce) 1f else 0f) }
    val sx = remember { Animatable(if (reduce) 1f else JellyCurve.saveSuccess.first().scaleX) }
    val sy = remember { Animatable(if (reduce) 1f else JellyCurve.saveSuccess.first().scaleY) }
    val textAlpha = remember { Animatable(if (reduce) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduce) {
            // Reduce Motion: no squash / stretch — the jelly and the message, briefly (same with or without Related).
            delay((if (kind == SaveFeedbackKind.FirstRecord) SaveFeedbackTiming.REDUCED_FIRST else SaveFeedbackTiming.REDUCED_SAVED).toLong())
            complete()
            return@LaunchedEffect
        }
        launch { scrim.animateTo(1f, tween(SaveFeedbackTiming.SCRIM_IN)) }
        launch { jellyAlpha.animateTo(1f, tween(JellyCurve.SAVE_APPEAR_MS)) }
        launch {
            delay(SaveFeedbackTiming.TEXT_DELAY.toLong())
            textAlpha.animateTo(1f, tween(SaveFeedbackTiming.TEXT_IN))
        }
        launch { sx.animateTo(1f, JellyMotion.spec(JellyCurve.saveSuccess) { it.scaleX }) }
        sy.animateTo(1f, JellyMotion.spec(JellyCurve.saveSuccess) { it.scaleY })
        val hold = when {
            kind == SaveFeedbackKind.FirstRecord -> SaveFeedbackTiming.HOLD_FIRST
            toRelated() -> SaveFeedbackTiming.HOLD_RELATED
            else -> SaveFeedbackTiming.HOLD_SAVED
        }
        delay(hold.toLong())
        complete()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f * scrim.value))
            // swallow touches on the screen below; a tap skips the rest of the hold
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { complete() },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(t.spacing.md),
            modifier = Modifier.align(BiasAlignment(0f, -0.15f)).padding(horizontal = t.spacing.xxl),
        ) {
            EmotionMarker(
                emotion = emotion ?: Emotion.SO_SO,
                size = 56.dp,
                modifier = Modifier.graphicsLayer {
                    alpha = jellyAlpha.value
                    scaleX = sx.value
                    scaleY = sy.value
                    transformOrigin = TransformOrigin(0.5f, 0.8f) // lands on its base
                },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                when (kind) {
                    SaveFeedbackKind.Saved -> Text(
                        SaveFeedbackCopy.SAVED,
                        style = MaterialTheme.typography.titleMedium,
                        color = t.textPrimary,
                    )
                    SaveFeedbackKind.FirstRecord -> {
                        Text(
                            SaveFeedbackCopy.FIRST_TITLE,
                            style = MaterialTheme.typography.titleMedium,
                            color = t.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            SaveFeedbackCopy.FIRST_BODY,
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
