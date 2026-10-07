package app.oneulmundeuk.related.qwen

import app.oneulmundeuk.related.JudgeResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.security.MessageDigest

/**
 * judge_v1 exactly as the M5 benchmark and the Android PoC used it (experiments/related/llm_judge.py ·
 * strong_judge.validate · experiments/android-qwen-poc/poc_judge.py). Pure: no model, no Android.
 *
 *  - prompt: `assets/judge_v1.txt`, byte-identical to experiments/related/prompts/judge_v1.txt (sha256 [PROMPT_SHA256]),
 *    split into "### SYSTEM" / "### USER" like `load_prompt`, `{current}` then `{past}` replaced like `messages`.
 *  - output: JSON schema {reason: string, label: 0|1|2} ([SCHEMA_JSON], same key order as the PoC request), checked
 *    with the same rules as `validate`: truncated / malformed JSON / label not an int 0..2 (bool, float, string
 *    rejected) / empty reason → [JudgeResult.Invalid] (that pair is FAILED, not retried — M5 "1 attempt").
 */
object JudgeV1 {
    const val ASSET = "judge_v1.txt"
    const val PROMPT_SHA256 = "553ab6176c0293f9aff2509a50481e5302687a6183e2de34cd52dfbb5f3f94af"

    /** `llm_judge.SCHEMA` serialized like the PoC request body (Python json.dumps order, compact). */
    const val SCHEMA_JSON =
        """{"type":"object","properties":{"reason":{"type":"string"},"label":{"type":"integer","enum":[0,1,2]}},"required":["reason","label"]}"""

    class Prompt(val system: String, private val userTemplate: String) {
        /** `llm_judge.messages`: {current} first, then {past} (plain replace, same order). */
        fun user(current: String, past: String): String = userTemplate.replace("{current}", current).replace("{past}", past)
    }

    private val SECTIONS = Regex("""\s*### SYSTEM\n(.*?)\n### USER\n(.*)""", RegexOption.DOT_MATCHES_ALL)

    /** Parses the prompt file; refuses anything that is not the benchmarked judge_v1 (sha256 mismatch). */
    fun parse(bytes: ByteArray): Prompt {
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(sha == PROMPT_SHA256) { "judge_v1 prompt sha256 $sha != $PROMPT_SHA256" }
        val m = requireNotNull(SECTIONS.matchEntire(String(bytes, Charsets.UTF_8))) { "'### SYSTEM' / '### USER' not found" }
        val system = m.groupValues[1].trim()
        val user = m.groupValues[2].trim()
        require("{current}" in user && "{past}" in user) { "user template without {current} / {past}" }
        return Prompt(system, user)
    }

    /** `strong_judge.validate` / `llm_judge.judge_once` output rules. */
    fun validate(content: String, truncated: Boolean): JudgeResult {
        if (truncated) return JudgeResult.Invalid("truncated (n_predict limit)")
        val element = try {
            Json.parseToJsonElement(content)
        } catch (e: SerializationException) {
            return JudgeResult.Invalid("malformed JSON")
        } catch (e: IllegalArgumentException) {
            return JudgeResult.Invalid("malformed JSON")
        }
        val obj = element as? JsonObject // not an object → no label (Python: obj.get on a non-dict → invalid label)
        val label = (obj?.get("label") as? JsonPrimitive)
            ?.takeIf { !it.isString && it.booleanOrNull == null && INT.matches(it.content) }
            ?.content?.toIntOrNull()
        if (label == null || label !in 0..2) return JudgeResult.Invalid("invalid label")
        val reason = (obj?.get("reason") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (reason.isNullOrBlank()) return JudgeResult.Invalid("empty reason")
        return JudgeResult.Label(label)
    }

    private val INT = Regex("-?(0|[1-9][0-9]*)")
}
