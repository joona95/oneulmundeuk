package app.oneulmundeuk.ui.components

/** One squash & stretch pose. */
data class JellyPose(val atMillis: Int, val scaleX: Float, val scaleY: Float)

/**
 * Selection squash & stretch for the emotion jelly. Pure Kotlin (no Compose) so it can be unit-tested.
 * Read as: pressed flat and wide → rebound narrow and tall → a tiny overshoot → rest. Width and height
 * move in opposite directions so the jelly keeps its "volume" (scaleX × scaleY stays near 1) — that is
 * what reads as soft, rather than a plain zoom in/out.
 */
object JellyCurve {
    val select: List<JellyPose> = listOf(
        JellyPose(0, 1f, 1f),
        JellyPose(90, 1.18f, 0.80f), // squash
        JellyPose(210, 0.92f, 1.12f), // stretch (rebound)
        JellyPose(320, 1.03f, 0.98f), // small settle overshoot
        JellyPose(420, 1f, 1f),
    )

    /**
     * Save success "통!": the chosen emotion's jelly pops in (0.85 → 1.05, uniform, fading in), then the
     * same squash → stretch → settle language as [select], a little stronger. Alpha is 0 → 1 over the
     * first segment ([SAVE_APPEAR_MS]).
     */
    val saveSuccess: List<JellyPose> = listOf(
        JellyPose(0, 0.85f, 0.85f), // appear (alpha 0)
        JellyPose(100, 1.05f, 1.05f), // pop in (alpha 1)
        JellyPose(220, 1.20f, 0.78f), // squash — "통!"
        JellyPose(360, 0.92f, 1.12f), // stretch (rebound)
        JellyPose(500, 1.03f, 0.98f), // small settle overshoot
        JellyPose(600, 1f, 1f),
    )
    const val SAVE_APPEAR_MS = 100
}
