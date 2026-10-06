package app.oneulmundeuk

import app.oneulmundeuk.data.db.RelatedResultRow
import app.oneulmundeuk.related.RelatedText
import app.oneulmundeuk.related.currentResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/** M6-2: text version (normalize + hash) and the pure "still current?" result filter. */
class RelatedTextTest {
    @Test
    fun hashIsVersionedSha256OfNormalizedText() {
        // SHA-256("abc")
        assertEquals("t1:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", RelatedText.hash("abc"))
        assertEquals(RelatedText.hash("오늘 생각"), RelatedText.hash("오늘 생각"))
        assertTrue(Regex("t1:[0-9a-f]{64}").matches(RelatedText.hash("아무 글")))
    }

    @Test
    fun sameTextDespiteEncodingLineEndingsAndOuterWhitespace() {
        val composed = "회의에서 말하는 게 편해짐"
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)
        assertNotEquals(composed, decomposed)
        assertEquals(RelatedText.hash(composed), RelatedText.hash(decomposed))
        assertEquals(RelatedText.hash("첫 줄\n둘째 줄"), RelatedText.hash("첫 줄\r\n둘째 줄"))
        assertEquals(RelatedText.hash("첫 줄\n둘째 줄"), RelatedText.hash("첫 줄\r둘째 줄"))
        assertEquals(RelatedText.hash("생각"), RelatedText.hash("  생각 \n"))
        assertTrue(RelatedText.sameText("\t생각", "생각"))
    }

    @Test
    fun meaningfulDifferencesAreNewVersions() {
        val base = RelatedText.hash("오늘은 괜찮았다")
        listOf("오늘은  괜찮았다", "오늘은 괜찮았다.", "오늘은 괜찮았다!", "오늘은\n괜찮았다", "오늘은 괜찮았다 🙂").forEach {
            assertNotEquals(it, base, RelatedText.hash(it))
        }
        assertNotEquals(RelatedText.hash("Swift"), RelatedText.hash("swift")) // no case folding
    }

    private fun row(id: String, sim: Float, target: String = "지금", candidate: String = "예전 $id",
                    targetHash: String = RelatedText.hash(target), candidateHash: String = RelatedText.hash(candidate),
                    analysisHash: String = RelatedText.hash(target)) =
        RelatedResultRow(id, sim, targetHash, candidateHash, analysisHash, target, candidate)

    @Test
    fun currentResultsKeepOrderAndCutAtFive() {
        val rows = (1..7).map { row("p$it", 1f - it / 10f) }
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5"), currentResults(rows))
        assertEquals(emptyList<String>(), currentResults(emptyList()))
    }

    @Test
    fun staleVersionsAreDroppedNotReplaced() {
        val rows = listOf(
            row("a", .9f),
            row("b", .8f, candidateHash = RelatedText.hash("예전 b의 옛 글")),   // candidate edited since
            row("c", .7f, targetHash = RelatedText.hash("옛 지금")),            // target edited since
            row("d", .6f, analysisHash = RelatedText.hash("옛 지금")),          // analysis for an old target text
            row("e", .5f),
        )
        assertEquals(listOf("a", "e"), currentResults(rows))
    }
}
