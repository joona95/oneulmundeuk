package app.placeholder.journal

import app.placeholder.journal.related.RelatedCandidate
import app.placeholder.journal.related.RelatedPolicy
import app.placeholder.journal.related.cosine
import app.placeholder.journal.related.selectWorthShowing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5 policy rules kept for M6 (label 2 only → similarity DESC → ≤ 5, never padded). Moved from the M5 draft finder test. */
class RelatedSelectionTest {
    private fun c(id: String, sim: Float) = RelatedCandidate(id, sim)

    @Test
    fun policyConstants() {
        assertEquals(30, RelatedPolicy.CANDIDATE_LIMIT)
        assertEquals(5, RelatedPolicy.MAX_RESULTS)
        assertEquals(2, RelatedPolicy.LABEL_WORTH_SHOWING)
    }

    @Test
    fun onlyLabel2IsShownNever1Or0OrFailed() {
        val judged = listOf(c("a", .9f) to 1, c("b", .8f) to 2, c("c", .7f) to 0, c("d", .6f) to null, c("e", .5f) to 2)
        assertEquals(listOf("b", "e"), selectWorthShowing(judged))
    }

    @Test
    fun fewerThanFiveIsNotPaddedAndNoneIsEmpty() {
        assertEquals(listOf("x"), selectWorthShowing(listOf(c("x", .4f) to 2, c("y", .9f) to 1)))
        assertTrue(selectWorthShowing(listOf(c("y", .9f) to 1, c("z", .8f) to 0)).isEmpty())
        assertTrue(selectWorthShowing(emptyList()).isEmpty())
    }

    @Test
    fun moreThanFiveKeepsTopFiveBySimilarityDesc() {
        val judged = (1..8).map { c("p$it", it / 10f) to 2 }.shuffled(java.util.Random(7))
        assertEquals(listOf("p8", "p7", "p6", "p5", "p4"), selectWorthShowing(judged))
        assertEquals(listOf("p8"), selectWorthShowing(judged, limit = 1))
    }

    @Test
    fun orderComesFromSimilarityWithIdTieBreakNotInputOrder() {
        val judged = listOf(c("b", .61f) to 2, c("a", .61f) to 2, c("z", .83f) to 2, c("q", .99f) to 1)
        assertEquals(listOf("z", "a", "b"), selectWorthShowing(judged))
    }

    @Test
    fun labelValidation() {
        listOf(0, 1, 2).forEach { assertTrue(RelatedPolicy.isValidLabel(it)) }
        listOf(null, -1, 3, 7).forEach { assertFalse(RelatedPolicy.isValidLabel(it)) }
    }

    @Test
    fun cosineBasics() {
        assertEquals(1f, cosine(floatArrayOf(2f, 0f), floatArrayOf(5f, 0f)), 1e-6f)
        assertEquals(0f, cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 3f)), 1e-6f)
        assertEquals(0f, cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 1e-6f)
        assertEquals(0.7071f, cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 1f)), 1e-4f)
    }
}
