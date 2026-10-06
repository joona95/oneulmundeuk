import app.oneulmundeuk.related.e5.XlmrTokenizer
import java.io.File
import java.util.Base64

/** kotlinc + java: compares XlmrTokenizer ids with the Python reference ids (out/tokens.tsv). Exit 1 on any mismatch. */
fun main(args: Array<String>) {
    val t0 = System.nanoTime()
    val tok = File(args[0]).bufferedReader(Charsets.UTF_8).useLines { XlmrTokenizer.load(it) }
    val loadMs = (System.nanoTime() - t0) / 1_000_000
    var ok = 0
    var bad = 0
    var tokenMs = 0L
    File(args[1]).readLines(Charsets.UTF_8).forEach { line ->
        val f = line.split('\t')
        val text = String(Base64.getDecoder().decode(f[1]), Charsets.UTF_8)
        val want = f[2].split(',').map { it.toInt() }
        val s = System.nanoTime()
        val got = tok.encode(text).toList()
        tokenMs += System.nanoTime() - s
        if (got == want) ok++ else {
            bad++
            val i = got.zip(want).indexOfFirst { (a, b) -> a != b }.let { if (it < 0) minOf(got.size, want.size) else it }
            println("MISMATCH ${f[0]} at $i: got ${got.subList(maxOf(0, i - 3), minOf(got.size, i + 4))} want ${want.subList(maxOf(0, i - 3), minOf(want.size, i + 4))} (len ${got.size}/${want.size}) text=${text.take(60).replace("\n", "\\n")}")
        }
    }
    println("tokenizer parity: $ok / ${ok + bad} identical · load ${loadMs} ms · tokenize total ${tokenMs / 1_000_000} ms")
    if (bad > 0) kotlin.system.exitProcess(1)
}
