package app.oneulmundeuk

import app.oneulmundeuk.data.db.RecordEntity
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.related.RelatedRecords
import app.oneulmundeuk.ui.components.RelatedNavTiming
import app.oneulmundeuk.ui.components.SaveFeedbackEnd
import app.oneulmundeuk.ui.components.SaveFeedbackKind
import app.oneulmundeuk.ui.components.SaveFeedbackTiming
import app.oneulmundeuk.ui.components.saveFeedbackEnd
import app.oneulmundeuk.ui.components.SaveFollowUp
import app.oneulmundeuk.ui.editor.RelatedGrace
import app.oneulmundeuk.ui.editor.followUpAfterSave
import app.oneulmundeuk.ui.editor.relatedWithin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M6-4 save → related grace window. Virtual time (runTest) and a controllable results Flow stand in for the
 * repository, so nothing depends on real delays. `null` = what `RelatedRepository.observeRelated` emits for
 * 0 results, PENDING, RUNNING, stale or no model.
 */
class SaveRelatedGraceTest {
    private fun rec(id: String) = RecordWithCategory(RecordEntity(id, "t-$id", 0, 0, null, null, null), categoryName = null)
    private val ready = RelatedRecords(rec("now"), listOf(rec("old")))

    @Test
    fun onlyANewNonFirstRecordWaits() {
        assertEquals(SaveFollowUp.Waiting, followUpAfterSave(isNewRecord = true, firstRecord = false))
        assertEquals(SaveFollowUp.None, followUpAfterSave(isNewRecord = true, firstRecord = true)) // first record: its own copy only
        assertEquals(SaveFollowUp.None, followUpAfterSave(isNewRecord = false, firstRecord = false)) // edit: plain ending
    }

    @Test
    fun resultsReadyWithinTheWindowContinueToRelated() = runTest {
        val results = MutableStateFlow<RelatedRecords?>(null) // PENDING → RUNNING → DONE
        launch { delay(RelatedGrace.WINDOW_MS - 100); results.value = ready }
        assertEquals(SaveFollowUp.Related, relatedWithin(results))
        assertEquals(RelatedGrace.WINDOW_MS - 100, currentTime) // decided as soon as they arrive
    }

    @Test
    fun alreadyReadyIsImmediate() = runTest {
        assertEquals(SaveFollowUp.Related, relatedWithin(MutableStateFlow(ready)))
        assertEquals(0L, currentTime)
    }

    @Test
    fun noResultsPendingOrRunningEndNormallyAtTheWindow() = runTest {
        assertEquals(SaveFollowUp.None, relatedWithin(MutableStateFlow(null)))
        assertEquals(RelatedGrace.WINDOW_MS, currentTime) // never longer than the window
    }

    @Test
    fun resultsAfterTheWindowNeverCount() = runTest {
        val results = MutableStateFlow<RelatedRecords?>(null)
        launch { delay(RelatedGrace.WINDOW_MS + 1); results.value = ready }
        assertEquals(SaveFollowUp.None, relatedWithin(results))
    }

    /** The 500 ms pause is navigation staging for results already there — only for a normal save that found them. */
    @Test
    fun relatedNavigationDelayOnlyWhenResultsWereReady() {
        assertEquals(500L, RelatedNavTiming.RELATED_NAV_DELAY_MS)
        assertEquals(SaveFeedbackEnd(toRelated = true, delayMs = 500L), saveFeedbackEnd(SaveFeedbackKind.Saved, SaveFollowUp.Related))
        val back = SaveFeedbackEnd(toRelated = false, delayMs = 0L) // no extra wait without results
        assertEquals(back, saveFeedbackEnd(SaveFeedbackKind.Saved, SaveFollowUp.None))
        assertEquals(back, saveFeedbackEnd(SaveFeedbackKind.Saved, SaveFollowUp.Waiting)) // tapped before decided
        assertEquals(back, saveFeedbackEnd(SaveFeedbackKind.FirstRecord, SaveFollowUp.None)) // first record unchanged
        assertEquals(back, saveFeedbackEnd(SaveFeedbackKind.FirstRecord, SaveFollowUp.Related))
    }

    /** Two independent policies: feedback / grace = 1500 ms; the related pause comes after, not inside the window. */
    @Test
    fun relatedPauseIsIndependentOfTheGraceWindow() {
        assertEquals(1500L, RelatedGrace.WINDOW_MS)
        assertEquals(2000L, SaveFeedbackTiming.SAVED_TOTAL + RelatedNavTiming.RELATED_NAV_DELAY_MS) // Related Memories ≈ 2 s
        assertEquals(SaveFeedbackTiming.SAVED_TOTAL.toLong(), RelatedGrace.WINDOW_MS) // the pause does not widen the window
    }

    @Test
    fun windowIsExactlyTheNormalFeedbackLength() {
        // 1500 ms: the save feedback is neither stretched to wait for AI nor cut short when results come early.
        assertEquals(1500L, RelatedGrace.WINDOW_MS)
        assertEquals((SaveFeedbackTiming.MOTION + SaveFeedbackTiming.HOLD_SAVED).toLong(), RelatedGrace.WINDOW_MS)
        assertEquals(SaveFeedbackTiming.REDUCED_SAVED.toLong(), RelatedGrace.WINDOW_MS)
    }
}
