package app.placeholder.journal

import app.placeholder.journal.ui.components.JellyCurve
import app.placeholder.journal.ui.splash.BRAND_NAME
import app.placeholder.journal.ui.splash.SplashMotion
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
        // Tempo pass: the whole intro reads as a splash (~0.9–1.1s), not an intro animation.
        assertTrue(SplashMotion.TOTAL in 900..1100)
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
        assertTrue(SplashMotion.HOLD <= 150)
    }

    @Test
    fun brandNameIsTheProductNameNotTheInternalCodename() {
        assertEquals("오늘문득", BRAND_NAME)
        assertTrue(!BRAND_NAME.contains("Echo", ignoreCase = true))
    }
}
