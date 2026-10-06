package app.oneulmundeuk.related.e5

import java.util.Base64

/**
 * Pure-Kotlin port of the HF fast tokenizer of dragonkue/multilingual-e5-small-ko-v2 (= XLM-R tokenizer.json), so the app
 * needs no tokenizer native library. Reads the compact file made by make_tokenizer_artifact.py. Pipeline, as in HF
 * `tokenizers` (Rust):
 *  1. special tokens (<s> <pad> </s> <unk> <mask>) are cut out of the raw text first
 *  2. normalizer: Precompiled (SentencePiece nmt_nfkc charsmap, per grapheme — same quirks as spm_precompiled)
 *     then Replace(" {2,}" → " ")
 *  3. pre-tokenizer: Metaspace(▁, prepend always, split MergedWithNext)
 *  4. model: Unigram Viterbi (scores, unk = min_score − 10, consecutive unks fused)
 *  5. post: <s> … </s>, truncated to max_length (512) like sentence-transformers.
 * Parity with the Python reference is checked by TokenizerParity.kt (experiments/e5-android).
 */
class XlmrTokenizer private constructor(
    private val pieceToId: HashMap<String, Int>,
    private val scores: DoubleArray,
    private val maxPieceCodePoints: Int,
    private val charsmap: Charsmap,
    private val added: List<Pair<String, Int>>,
    val unkId: Int,
    val bosId: Int,
    val eosId: Int,
    val padId: Int,
    val maxLength: Int,
    private val minScore: Double,
) {
    /** Token ids incl. <s> / </s>, at most [maxLength]. attention_mask = all ones for a single text. */
    fun encode(text: String): IntArray {
        val ids = ArrayList<Int>()
        for ((segment, specialId) in splitAdded(text)) {
            if (specialId != null) { ids += specialId; continue }
            val normalized = REPEATED_SPACES.replace(charsmap.normalize(segment), " ")
            for (word in metaspace(normalized)) unigram(word, ids)
        }
        val body = if (ids.size > maxLength - 2) ids.subList(0, maxLength - 2) else ids
        return IntArray(body.size + 2).also { out ->
            out[0] = bosId
            body.forEachIndexed { i, id -> out[i + 1] = id }
            out[out.size - 1] = eosId
        }
    }

    /** Special tokens are matched on the raw text (leftmost, longest), never normalized. Empty pieces are dropped. */
    private fun splitAdded(text: String): List<Pair<String, Int?>> {
        val out = ArrayList<Pair<String, Int?>>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val hit = added.filter { text.startsWith(it.first, i) }.maxByOrNull { it.first.length }
            if (hit == null) { i++; continue }
            if (i > start) out += text.substring(start, i) to null
            out += hit.first to hit.second
            i += hit.first.length
            start = i
        }
        if (start < text.length) out += text.substring(start) to null
        return out
    }

    private fun metaspace(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        var t = s.replace(' ', META)
        if (t[0] != META) t = META + t
        val words = ArrayList<String>()
        var from = 0
        for (i in 1 until t.length) if (t[i] == META) { words += t.substring(from, i); from = i }
        words += t.substring(from)
        return words
    }

    /** HF Unigram.encode_optimized: best path by summed scores; ties keep the earlier start; unknown chars → fused unk. */
    private fun unigram(word: String, out: MutableList<Int>) {
        val cps = word.codePoints().toArray()
        val n = cps.size
        val bestScore = DoubleArray(n + 1)
        val startAt = IntArray(n + 1) { -1 }
        val idAt = IntArray(n + 1)
        val unkScore = minScore - UNK_PENALTY
        for (s in 0 until n) {
            if (s > 0 && startAt[s] == -1) continue // unreachable (cannot happen: every char has a node)
            val base = bestScore[s]
            var hasSingle = false
            val sb = StringBuilder()
            for (len in 1..minOf(maxPieceCodePoints, n - s)) {
                sb.appendCodePoint(cps[s + len - 1])
                val id = pieceToId[sb.toString()] ?: continue
                val e = s + len
                val cand = scores[id] + base
                if (startAt[e] == -1 || cand > bestScore[e]) { bestScore[e] = cand; startAt[e] = s; idAt[e] = id }
                if (len == 1) hasSingle = true
            }
            if (!hasSingle) {
                val e = s + 1
                val cand = unkScore + base
                if (startAt[e] == -1 || cand > bestScore[e]) { bestScore[e] = cand; startAt[e] = s; idAt[e] = unkId }
            }
        }
        val rev = ArrayList<Int>()
        var e = n
        var inUnk = false
        while (e > 0) {
            val s = startAt[e]
            if (idAt[e] == unkId) { if (!inUnk) rev += unkId; inUnk = true } else { rev += idAt[e]; inUnk = false }
            e = s
        }
        for (i in rev.indices.reversed()) out += rev[i]
    }

    /** SentencePiece precompiled charsmap: double-array trie over UTF-8 bytes → offset into a NUL-separated string pool. */
    private class Charsmap(blob: ByteArray) {
        private val trie: IntArray
        private val pool: ByteArray

        init {
            val trieBytes = le32(blob, 0)
            trie = IntArray(trieBytes / 4) { le32(blob, 4 + it * 4) }
            pool = blob.copyOfRange(4 + trieBytes, blob.size)
        }

        /** spm_precompiled.transform: the FIRST (shortest) prefix match replaces the whole chunk. */
        private fun transform(chunk: ByteArray): String? {
            var pos = 0
            var unit = trie[pos]
            pos = pos xor offset(unit)
            for (b in chunk) {
                val c = b.toInt() and 0xFF
                if (c == 0) break
                pos = pos xor c
                unit = trie[pos]
                if (label(unit) != c) return null
                pos = pos xor offset(unit)
                if (hasLeaf(unit)) {
                    val start = value(trie[pos])
                    var end = start
                    while (end < pool.size && pool[end].toInt() != 0) end++
                    return String(pool, start, end - start, Charsets.UTF_8)
                }
            }
            return null
        }

        /** tokenizers Precompiled.normalize: per extended grapheme (< 6 UTF-8 bytes as a whole), else per char. */
        fun normalize(s: String): String {
            val out = StringBuilder(s.length)
            var start = 0
            while (start < s.length) {
                val end = Graphemes.clusterEnd(s, start)
                val g = s.substring(start, end)
                val gb = g.toByteArray(Charsets.UTF_8)
                val whole = if (gb.size < 6) transform(gb) else null
                if (whole != null) out.append(whole) else {
                    var i = 0
                    while (i < g.length) {
                        val cp = g.codePointAt(i)
                        val part = String(Character.toChars(cp))
                        out.append(transform(part.toByteArray(Charsets.UTF_8)) ?: part)
                        i += Character.charCount(cp)
                    }
                }
                start = end
            }
            return out.toString()
        }

        private fun offset(u: Int) = (u ushr 10) shl ((u and (1 shl 9)) ushr 6)
        private fun label(u: Int) = u and ((1 shl 31) or 0xFF)
        private fun value(u: Int) = u and 0x7FFFFFFF
        private fun hasLeaf(u: Int) = (u ushr 8) and 1 == 1

        private companion object {
            fun le32(b: ByteArray, i: Int) =
                (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
        }
    }

    companion object {
        private const val META = '▁'
        private const val UNK_PENALTY = 10.0
        private val REPEATED_SPACES = Regex(" {2,}")

        /** Parses the artifact text (see make_tokenizer_artifact.py). Throws on an unexpected format. */
        fun load(lines: Sequence<String>): XlmrTokenizer {
            val it = lines.iterator()
            require(it.next() == "#oneulmundeuk xlmr-unigram v1") { "unknown tokenizer format" }
            val kv = HashMap<String, String>()
            val added = ArrayList<Pair<String, Int>>()
            var count = -1
            while (count < 0) {
                val line = it.next()
                val f = line.split('\t')
                when (f[0]) {
                    "added" -> added += f[2] to f[1].toInt()
                    "vocab" -> count = f[1].toInt()
                    else -> kv[f[0]] = f[1]
                }
            }
            val map = HashMap<String, Int>(count * 2)
            val scores = DoubleArray(count)
            var maxCp = 1
            for (id in 0 until count) {
                val line = it.next()
                val tab = line.lastIndexOf('\t')
                val piece = line.substring(0, tab)
                scores[id] = line.substring(tab + 1).toDouble()
                map[piece] = id
                maxCp = maxOf(maxCp, piece.codePointCount(0, piece.length))
            }
            return XlmrTokenizer(
                map, scores, maxCp, Charsmap(Base64.getDecoder().decode(kv.getValue("charsmap"))), added,
                unkId = kv.getValue("unk_id").toInt(), bosId = kv.getValue("bos_id").toInt(), eosId = kv.getValue("eos_id").toInt(),
                padId = kv.getValue("pad_id").toInt(), maxLength = kv.getValue("max_length").toInt(),
                minScore = kv.getValue("min_score").toDouble(),
            )
        }
    }
}
