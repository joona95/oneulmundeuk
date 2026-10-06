package app.oneulmundeuk.ui.components

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What was saved. The first record ever gets a slightly longer, warmer message; the motion is the same. */
enum class SaveFeedbackKind { Saved, FirstRecord }

/**
 * Where the save ends (M6-4), decided by the editor within the grace window. [Waiting] = still open;
 * [Related] = the new record's related results were ready in time → Related Memories; [None] = the usual ending.
 * The overlay itself never shows anything about it — only "저장했어요" + jelly.
 */
enum class SaveFollowUp { None, Waiting, Related }

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
    /**
     * Normal save = 1500 ms in total (600 ms jelly + 900 ms still "저장했어요"), the SAME whether or not related results
     * come (M6-4): it is also the grace window (`RelatedGrace.WINDOW_MS`), and the move to Related Memories / back
     * only happens when it ends — never earlier because results were ready sooner.
     */
    const val SAVED_TOTAL = 1500
    const val HOLD_SAVED = 900
    const val HOLD_FIRST = 1100
    const val REDUCED_SAVED = SAVED_TOTAL // Reduce Motion: same length, so it lines up with the grace window too
    const val REDUCED_FIRST = 1500
}

/**
 * Navigation choreography after the save feedback (M6-4) — a separate policy from [SaveFeedbackTiming] and the grace
 * window. When the record's related results were ALREADY ready within the grace window, the screen stays on the
 * finished "저장했어요" + jelly for [RELATED_NAV_DELAY_MS] more, then Related Memories opens (≈ 2 s after saving).
 * It is staging only: no result is waited for during it. Without results the feedback ends at 1500 ms, no pause.
 */
object RelatedNavTiming {
    const val RELATED_NAV_DELAY_MS = 500L
}

/** How a save feedback ends: back to the origin, or (after [delayMs]) to Related Memories. */
data class SaveFeedbackEnd(val toRelated: Boolean, val delayMs: Long)

/**
 * Pure: only a normal save ([SaveFeedbackKind.Saved]) whose follow-up was decided as [SaveFollowUp.Related] goes to
 * Related Memories, after [RelatedNavTiming.RELATED_NAV_DELAY_MS]. Everything else (first record, edit, no results,
 * still waiting) goes back right away.
 */
fun saveFeedbackEnd(kind: SaveFeedbackKind, followUp: SaveFollowUp): SaveFeedbackEnd =
    if (kind == SaveFeedbackKind.Saved && followUp == SaveFollowUp.Related) {
        SaveFeedbackEnd(toRelated = true, delayMs = RelatedNavTiming.RELATED_NAV_DELAY_MS)
    } else {
        SaveFeedbackEnd(toRelated = false, delayMs = 0L)
    }

/**
 * Save-success feedback: a lightweight overlay over the current screen with the record's emotion jelly
 * doing one "통!" (pop-in → squash → stretch → settle), and a short message. Shown only AFTER the save
 * has succeeded — it never delays the database write. Calls [onFinished] once; the caller then continues
 * its normal navigation. Tapping skips the remaining hold.
 *
 * No emotion selected → the neutral (그냥 그래) jelly color is used as the plain mark.
 *
 * The feedback length never depends on related-record analysis. Only WHERE it ends can change (M6-4): a new record
 * whose related results were ready within the grace window stays [RelatedNavTiming.RELATED_NAV_DELAY_MS] longer on the
 * finished feedback, then goes to Related Memories ([onFinishedToRelated]) instead of back ([saveFeedbackEnd]).
 * No "문득" copy, CTA, spinner or "분석 중" here. A tap skips the hold and ends with what is known at that moment.
 * Reduce Motion keeps its usual short static frame.
 */
@Composable
fun SaveSuccessOverlay(
    emotion: Emotion?,
    kind: SaveFeedbackKind,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    followUp: SaveFollowUp = SaveFollowUp.None,
    /** Instead of [onFinished] when [followUp] is [SaveFollowUp.Related] at the end: open Related Memories. */
    onFinishedToRelated: () -> Unit = onFinished,
) {
    val t = AppTheme.tokens
    val reduce = rememberReduceMotion()
    val finish by rememberUpdatedState(onFinished)
    val finishToRelated by rememberUpdatedState(onFinishedToRelated)
    val currentFollowUp by rememberUpdatedState(followUp)
    val scope = rememberCoroutineScope()
    var ending by remember { mutableStateOf(false) }

    /** Ends once: back right away, or the related pause and then Related Memories. */
    suspend fun finishWith(decided: SaveFollowUp) {
        if (ending) return
        ending = true
        val end = saveFeedbackEnd(kind, decided)
        if (end.toRelated) {
            delay(end.delayMs)
            finishToRelated()
        } else {
            finish()
        }
    }

    /**
     * End of the normal hold. The grace window has the same length and starts at the save commit (just before this
     * overlay), so it is normally decided by now; at most a frame or two is waited for it — never longer. Once decided it does not change, so a
     * result after the window cannot redirect this save.
     */
    suspend fun end() {
        val decided = if (kind == SaveFeedbackKind.Saved) {
            snapshotFlow { currentFollowUp }.first { it != SaveFollowUp.Waiting }
        } else {
            SaveFollowUp.None
        }
        finishWith(decided)
    }

    val scrim = remember { Animatable(if (reduce) 1f else 0f) }
    val jellyAlpha = remember { Animatable(if (reduce) 1f else 0f) }
    val sx = remember { Animatable(if (reduce) 1f else JellyCurve.saveSuccess.first().scaleX) }
    val sy = remember { Animatable(if (reduce) 1f else JellyCurve.saveSuccess.first().scaleY) }
    val textAlpha = remember { Animatable(if (reduce) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduce) {
            // Reduce Motion: no squash / stretch — the jelly and the message, briefly.
            delay((if (kind == SaveFeedbackKind.FirstRecord) SaveFeedbackTiming.REDUCED_FIRST else SaveFeedbackTiming.REDUCED_SAVED).toLong())
            end()
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
        val hold = if (kind == SaveFeedbackKind.FirstRecord) SaveFeedbackTiming.HOLD_FIRST else SaveFeedbackTiming.HOLD_SAVED
        delay(hold.toLong())
        end()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f * scrim.value))
            // swallow touches on the screen below; a tap skips the rest of the hold
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { finishWith(currentFollowUp) } // still Waiting → back, like no results
            },
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
