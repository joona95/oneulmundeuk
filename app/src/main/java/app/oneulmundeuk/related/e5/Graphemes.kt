package app.oneulmundeuk.related.e5

/**
 * Extended grapheme cluster boundaries (Unicode UAX #29 rules GB3–GB13) — the segmentation HF `tokenizers` gets from the
 * Rust `unicode-segmentation` crate, which the Precompiled normalizer uses (a cluster under 6 UTF-8 bytes is normalized
 * as a whole). Neither java.text.BreakIterator on old JDKs nor the platform version per Android release is guaranteed to
 * match it, so the rules live here, with property tables from Character categories + the short explicit lists below.
 */
internal object Graphemes {
    private enum class P { CR, LF, CONTROL, EXTEND, ZWJ, RI, PREPEND, SPACING_MARK, L, V, T, LV, LVT, OTHER }

    /** End index (exclusive, UTF-16) of the cluster starting at [start]. */
    fun clusterEnd(s: String, start: Int): Int {
        var i = start
        var prevCp = s.codePointAt(i)
        var prev = prop(prevCp)
        var extPictZwjSeq = isExtPict(prevCp) // ExtPict Extend* (ZWJ)?  for GB11
        var riCount = if (prev == P.RI) 1 else 0
        i += Character.charCount(prevCp)
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val cur = prop(cp)
            val join = when {
                prev == P.CR && cur == P.LF -> true                                   // GB3
                prev == P.CR || prev == P.LF || prev == P.CONTROL -> false            // GB4
                cur == P.CR || cur == P.LF || cur == P.CONTROL -> false               // GB5
                prev == P.L && (cur == P.L || cur == P.V || cur == P.LV || cur == P.LVT) -> true // GB6
                (prev == P.LV || prev == P.V) && (cur == P.V || cur == P.T) -> true   // GB7
                (prev == P.LVT || prev == P.T) && cur == P.T -> true                  // GB8
                cur == P.EXTEND || cur == P.ZWJ -> true                               // GB9
                cur == P.SPACING_MARK -> true                                         // GB9a
                prev == P.PREPEND -> true                                             // GB9b
                prev == P.ZWJ && extPictZwjSeq && isExtPict(cp) -> true               // GB11
                prev == P.RI && cur == P.RI -> riCount % 2 == 1                       // GB12/13
                else -> false                                                         // GB999
            }
            if (!join) break
            // GB11 state: ExtPict (Extend)* ZWJ — stays true only along that shape
            extPictZwjSeq = when {
                isExtPict(cp) -> true
                cur == P.EXTEND || cur == P.ZWJ -> extPictZwjSeq && prev != P.ZWJ
                else -> false
            }
            riCount = if (cur == P.RI) riCount + 1 else 0
            prev = cur
            i += Character.charCount(cp)
        }
        return i
    }

    private fun prop(cp: Int): P {
        when (cp) {
            0x0D -> return P.CR
            0x0A -> return P.LF
            0x200D -> return P.ZWJ
            0x200C -> return P.EXTEND
        }
        if (cp in 0x1F1E6..0x1F1FF) return P.RI
        if (cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F || cp in EXTRA_EXTEND) return P.EXTEND
        if (cp in PREPEND) return P.PREPEND
        if (cp == 0x0E33 || cp == 0x0EB3) return P.SPACING_MARK
        if (cp in 0x1100..0x115F || cp in 0xA960..0xA97C) return P.L
        if (cp in 0x1160..0x11A7 || cp in 0xD7B0..0xD7C6) return P.V
        if (cp in 0x11A8..0x11FF || cp in 0xD7CB..0xD7FB) return P.T
        if (cp in 0xAC00..0xD7A3) return if ((cp - 0xAC00) % 28 == 0) P.LV else P.LVT
        return when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK -> P.EXTEND
            Character.COMBINING_SPACING_MARK -> if (cp in NOT_SPACING_MARK) P.OTHER else P.SPACING_MARK
            Character.CONTROL, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.FORMAT -> P.CONTROL
            Character.UNASSIGNED, Character.SURROGATE -> if (cp in 0xE0000..0xE0FFF) P.CONTROL else P.OTHER
            else -> P.OTHER
        }
    }

    /** Grapheme_Extend that are not Mn / Me (Mc with Other_Grapheme_Extend, halfwidth sound marks, …). */
    private val EXTRA_EXTEND = setOf(
        0x09BE, 0x09D7, 0x0B3E, 0x0B57, 0x0BBE, 0x0BD7, 0x0CC2, 0x0CD5, 0x0CD6, 0x0D3E, 0x0D57, 0x0DCF, 0x0DDF,
        0x1B35, 0x302E, 0x302F, 0xFF9E, 0xFF9F, 0x1D165, 0x1D16E, 0x1D16F, 0x1D170, 0x1D171, 0x1D172,
    )

    private val PREPEND = setOf(0x0600, 0x0601, 0x0602, 0x0603, 0x0604, 0x0605, 0x06DD, 0x070F, 0x0890, 0x0891, 0x08E2, 0x0D4E, 0x110BD, 0x110CD)

    /** Mc that UAX #29 does not treat as SpacingMark. */
    private val NOT_SPACING_MARK = setOf(
        0x102B, 0x102C, 0x1038, 0x1062, 0x1063, 0x1064, 0x1067, 0x1068, 0x1069, 0x106A, 0x106B, 0x106C, 0x106D, 0x1083,
        0x1087, 0x1088, 0x1089, 0x108A, 0x108B, 0x108C, 0x108F, 0x109A, 0x109B, 0x109C, 0x1A61, 0x1A63, 0x1A64, 0xAA7B, 0xAA7D, 0x11720, 0x11721,
    )

    private fun isExtPict(cp: Int): Boolean =
        cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 ||
            cp in 0x2194..0x2199 || cp in 0x21A9..0x21AA || cp in 0x231A..0x231B || cp == 0x2328 || cp == 0x2388 || cp == 0x23CF ||
            cp in 0x23E9..0x23F3 || cp in 0x23F8..0x23FA || cp == 0x24C2 || cp in 0x25AA..0x25AB || cp == 0x25B6 || cp == 0x25C0 ||
            cp in 0x25FB..0x25FE || cp in 0x2600..0x27BF || cp in 0x2934..0x2935 || cp in 0x2B05..0x2B07 || cp in 0x2B1B..0x2B1C ||
            cp == 0x2B50 || cp == 0x2B55 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299 ||
            (cp in 0x1F000..0x1FAFF && cp !in 0x1F1E6..0x1F1FF && cp !in 0x1F3FB..0x1F3FF) || cp in 0x1FC00..0x1FFFD
}
