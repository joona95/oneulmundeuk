package app.oneulmundeuk

import app.oneulmundeuk.related.e5.XlmrTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * The production tokenizer (related/e5) against the Python reference ids of experiments/e5-android. The artifact and
 * fixtures are generated locally (out/, not in git) — without them this test SKIPS.
 */
class E5TokenizerParityTest {
    private val out = File("../experiments/e5-android/out")

    @Test
    fun matchesPythonReference() {
        val tok = File(out, "e5-small-ko-v2-tokenizer.txt")
        assumeTrue("experiments/e5-android/out not generated — skipped", tok.isFile)
        val tokenizer = tok.bufferedReader(Charsets.UTF_8).useLines { XlmrTokenizer.load(it) }
        for (name in listOf("tokens.tsv", "tokens_fuzz.tsv")) {
            val f = File(out, name)
            if (!f.isFile) continue
            var bad = 0
            var n = 0
            f.forEachLine(Charsets.UTF_8) { line ->
                val p = line.split('\t')
                val text = String(Base64.getDecoder().decode(p[1]), Charsets.UTF_8)
                n++
                if (tokenizer.encode(text).toList() != p[2].split(',').map { it.toInt() }) bad++
            }
            assertEquals("$name: $bad / $n differ", 0, bad)
        }
    }
}
