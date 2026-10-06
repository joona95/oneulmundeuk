package app.oneulmundeuk

import app.oneulmundeuk.ui.components.JellyCurve
import app.oneulmundeuk.ui.components.SaveFeedbackCopy
import app.oneulmundeuk.ui.components.SaveFeedbackTiming
import app.oneulmundeuk.ui.splash.BRAND_NAME
import app.oneulmundeuk.ui.splash.SplashMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the M1.5 motion decisions: visible squash & stretch, short duration, no growth over time. */
class MotionSpecTest {
    private val curve = JellyCurve.select

    @Test
    fun jellyStartsAndEndsAtRest() {
        assertEquals(0, curve.first().atMillis)
        assertEquals(1f, curve.first().scaleX)
        assertEquals(1f, curve.first().scaleY)
        assertEquals(1f, curve.last().scaleX)
        assertEquals(1f, curve.last().scaleY)
    }

    @Test
    fun jellyDurationIsShort() {
        assertTrue(curve.last().atMillis in 350..450)
        assertTrue(curve.zipWithNext().all { (a, b) -> b.atMillis > a.atMillis })
    }

    @Test
    fun jellySquashesThenStretches() {
        val squash = curve[1]
        val stretch = curve[2]
        // squash: wider and flatter — clearly visible, not a subtle 5%
        assertTrue(squash.scaleX in 1.12f..1.22f)
        assertTrue(squash.scaleY in 0.74f..0.85f)
        // rebound: narrower and taller
        assertTrue(stretch.scaleX in 0.88f..0.96f)
        assertTrue(stretch.scaleY in 1.06f..1.16f)
    }

    @Test
    fun jellyRoughlyKeepsVolume() {
        // Opposite X/Y movement (not a uniform zoom): area stays within ±10%.
        curve.forEach { assertTrue("${it.atMillis}ms", it.scaleX * it.scaleY in 0.90f..1.10f) }
        curve.drop(1).dropLast(1).forEach { assertTrue((it.scaleX - 1f) * (it.scaleY - 1f) < 0f) }
    }

    @Test
    fun splashIsBriefAndHasClearSquash() {
        // Reads as a splash (~1.5s), not an intro animation.
        assertTrue(SplashMotion.TOTAL in 1450..1550)
        assertTrue(SplashMotion.SQUASH_X > 1.2f && SplashMotion.SQUASH_Y < 0.8f)
        assertTrue(SplashMotion.STRETCH_X < 1f && SplashMotion.STRETCH_Y > 1f)
        assertTrue(SplashMotion.REDUCED_HOLD < SplashMotion.TOTAL)
    }

    @Test
    fun splashTempoChangeKeepsTheDeformation() {
        // Faster, not weaker: the squash / stretch amounts are the M1.5 values.
        assertEquals(1.30f, SplashMotion.SQUASH_X)
        assertEquals(0.70f, SplashMotion.SQUASH_Y)
        assertEquals(0.90f, SplashMotion.STRETCH_X)
        assertEquals(1.15f, SplashMotion.STRETCH_Y)
        // Each phase is still long enough to be seen.
        assertTrue(SplashMotion.DROP >= 200 && SplashMotion.SQUASH >= 70 && SplashMotion.REBOUND >= 110)
    }

    @Test
    fun splashNameOverlapsTheSettle() {
        // The name starts during the rebound and is fully in before the jelly finishes settling.
        assertTrue(SplashMotion.NAME_DELAY_IN_REBOUND < SplashMotion.REBOUND)
        assertTrue(SplashMotion.NAME_DELAY_IN_REBOUND + SplashMotion.NAME_FADE <= SplashMotion.REBOUND + SplashMotion.SETTLE)
        assertTrue(SplashMotion.HOLD in 550..700) // a short, still brand moment before Home
    }

    @Test
    fun saveSuccessIsAShortClearPop() {
        val save = JellyCurve.saveSuccess
        assertEquals(0, save.first().atMillis)
        assertTrue(save.last().atMillis in 500..700)
        assertTrue(save.zipWithNext().all { (a, b) -> b.atMillis > a.atMillis })
        // pops in from slightly small, uniformly
        assertTrue(save.first().scaleX < 1f && save.first().scaleX == save.first().scaleY)
        assertEquals(1f, save.last().scaleX)
        assertEquals(1f, save.last().scaleY)
        // the "통!" is at least as strong as the selection squash, and keeps the volume rule
        val squash = save.maxBy { it.scaleX }
        assertTrue(squash.scaleX >= curve[1].scaleX && squash.scaleY <= curve[1].scaleY)
        save.drop(2).dropLast(1).forEach { assertTrue((it.scaleX - 1f) * (it.scaleY - 1f) < 0f) }
        save.drop(2).forEach { assertTrue("${it.atMillis}ms", it.scaleX * it.scaleY in 0.90f..1.10f) }
    }

    @Test
    fun saveFeedbackDoesNotHoldTheNormalFlow() {
        // normal save: jelly (600ms) + a still "저장했어요" = 1.5s, then navigation continues (back or Related Memories)
        assertEquals(1500, SaveFeedbackTiming.MOTION + SaveFeedbackTiming.HOLD_SAVED)
        assertEquals(SaveFeedbackTiming.SAVED_TOTAL, SaveFeedbackTiming.MOTION + SaveFeedbackTiming.HOLD_SAVED)
        assertEquals(SaveFeedbackTiming.SAVED_TOTAL, SaveFeedbackTiming.REDUCED_SAVED)
        // first record: same motion, a little longer so the message can be read
        assertTrue(SaveFeedbackTiming.HOLD_FIRST > SaveFeedbackTiming.HOLD_SAVED)
        assertEquals("저장했어요", SaveFeedbackCopy.SAVED)
        assertEquals("첫 생각을 남겼어요.", SaveFeedbackCopy.FIRST_TITLE)
    }

    @Test
    fun brandNameIsTheProductNameNotTheInternalCodename() {
        assertEquals("오늘문득", BRAND_NAME)
        assertTrue(!BRAND_NAME.contains("Echo", ignoreCase = true))
    }

    @Test
    fun relatedMemoriesEnterIsOneQuietFade() {
        val m = app.oneulmundeuk.ui.related.RelatedMemoriesMotion
        assertTrue(m.ENTER in 800..900)
        assertTrue(m.ENTER_REDUCED < m.ENTER)
        assertTrue(m.RISE_DP in 8..16)
    }

    @Test
    fun saveFeedbackLengthDoesNotDependOnRelatedResults() {
        // M6-4: one length for every normal save, with or without related results; it equals the grace window,
        // so results never shorten it and waiting for them never lengthens it.
        assertEquals(SaveFeedbackTiming.SAVED_TOTAL.toLong(), app.oneulmundeuk.ui.editor.RelatedGrace.WINDOW_MS)
        assertTrue(SaveFeedbackTiming.HOLD_FIRST > SaveFeedbackTiming.HOLD_SAVED) // first-record copy is read longer
        assertEquals(1100, SaveFeedbackTiming.HOLD_FIRST) // first-record timing unchanged
        assertEquals(1500, SaveFeedbackTiming.REDUCED_FIRST)
        // the related navigation pause is its own policy (500 ms), added only when results were ready
        assertEquals(500L, app.oneulmundeuk.ui.components.RelatedNavTiming.RELATED_NAV_DELAY_MS)
        assertEquals(1500, SaveFeedbackTiming.SAVED_TOTAL)
    }
}
