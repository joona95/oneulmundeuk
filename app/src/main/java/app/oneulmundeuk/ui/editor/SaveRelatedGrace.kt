package app.oneulmundeuk.ui.editor

import app.oneulmundeuk.related.RelatedRecords
import app.oneulmundeuk.ui.components.SaveFeedbackTiming
import app.oneulmundeuk.ui.components.SaveFollowUp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Save → related grace window (M6-4). PRODUCTION policy, independent of any model's speed:
 * after a new record has been saved, its related results are watched for at most [WINDOW_MS] — exactly the normal
 * "저장했어요" feedback length (1500 ms), so the feedback is never stretched to wait for AI and never cut short:
 * results ready earlier still wait for the feedback to end. The extra pause before Related Memories opens
 * (`RelatedNavTiming.RELATED_NAV_DELAY_MS`, 500 ms) is a separate staging policy and never extends this window.
 * Ready in time (DONE + ≥ 1 current result) → when "저장했어요" ends, Related Memories opens (no copy / CTA in the overlay).
 * Otherwise (0 results, PENDING / RUNNING, no model, too slow) → the usual save ending. Nothing is shown later.
 * The debug fake model's latency is a separate, debug-only knob (src/debug `DebugRelatedRuntime`).
 */
object RelatedGrace {
    const val WINDOW_MS = SaveFeedbackTiming.SAVED_TOTAL.toLong() // 1500
}

/** Only a new record that is not the first one can have earlier records to connect to. Edits keep the plain ending. */
fun followUpAfterSave(isNewRecord: Boolean, firstRecord: Boolean): SaveFollowUp =
    if (isNewRecord && !firstRecord) SaveFollowUp.Waiting else SaveFollowUp.None

/** Waits for the first non-null result within [windowMs]; a result after the window never counts. */
suspend fun relatedWithin(results: Flow<RelatedRecords?>, windowMs: Long = RelatedGrace.WINDOW_MS): SaveFollowUp =
    if (withTimeoutOrNull(windowMs) { results.first { it != null } } != null) SaveFollowUp.Related else SaveFollowUp.None
