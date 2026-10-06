package app.oneulmundeuk.related

import java.security.MessageDigest
import java.text.Normalizer

/**
 * "Is this still the same text?" for related-record caches (embeddings, judgments, analyses). Pure.
 *
 * Minimal normalization — only differences that cannot change what the text says:
 *  1. Unicode NFC (the same Hangul typed / pasted as composed or decomposed jamo is the same text)
 *  2. line endings CRLF / CR → LF
 *  3. trim leading / trailing whitespace (the editor already trims the end on save)
 * Nothing else: no case folding, no inner-whitespace collapsing, no punctuation / emoji removal. Any such edit is a
 * new text version and is re-analysed.
 *
 * [hash] = "t1:" + SHA-256(UTF-8 of the normalized text) in lowercase hex. The "t1" prefix is the rule version:
 * changing the rules changes every hash, so old caches can never be mistaken for current ones.
 */
object RelatedText {
    const val VERSION = "t1"

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

    fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalize(text).toByteArray(Charsets.UTF_8))
        return VERSION + ":" + digest.joinToString("") { "%02x".format(it) }
    }

    fun sameText(a: String, b: String): Boolean = normalize(a) == normalize(b)
}
