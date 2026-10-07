package app.oneulmundeuk

import app.oneulmundeuk.related.JudgeResult
import app.oneulmundeuk.related.qwen.ChatCompletion
import app.oneulmundeuk.related.qwen.Finish
import app.oneulmundeuk.related.qwen.JudgeV1
import app.oneulmundeuk.related.qwen.QwenEngine
import app.oneulmundeuk.related.qwen.QwenJudge
import app.oneulmundeuk.related.qwen.QwenRuntimeSupport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** judge_v1 prompt / schema / output rules = the M5 benchmark and Android PoC (llm_judge.py · strong_judge.validate). */
class QwenJudgeTest {
    private val asset = File("src/main/assets/judge_v1.txt").readBytes()
    private val benchmark = File("../experiments/related/prompts/judge_v1.txt")

    @Test
    fun promptIsTheBenchmarkedJudgeV1() {
        if (benchmark.isFile) assertTrue(asset.contentEquals(benchmark.readBytes())) // byte-identical copy
        val prompt = JudgeV1.parse(asset)
        assertTrue(prompt.system.startsWith("너는 개인 메모 앱에서"))
        assertTrue(prompt.system.endsWith("정해진 JSON 형식으로만 답한다."))
        assertEquals("현재 기록:\n\"\"\"\nA\n\"\"\"\n\n과거 기록:\n\"\"\"\nB\n\"\"\"", prompt.user("A", "B"))
        // llm_judge.messages: plain replace, {current} first — a "{past}" inside the current text is replaced too
        assertTrue(prompt.user("x {past}", "P").contains("x P"))
    }

    @Test
    fun anyOtherPromptIsRefused() {
        val changed = asset.copyOf().also { it[it.size - 1] = ' '.code.toByte() }
        try {
            JudgeV1.parse(changed)
            fail("modified prompt accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("sha256"))
        }
    }

    @Test
    fun schemaIsThePocRequestSchema() {
        // poc_judge.py: response_format.json_schema.schema = llm_judge.SCHEMA (server dumps it compact, keys in order)
        assertEquals(
            """{"type":"object","properties":{"reason":{"type":"string"},"label":{"type":"integer","enum":[0,1,2]}},"required":["reason","label"]}""",
            JudgeV1.SCHEMA_JSON,
        )
    }

    @Test
    fun outputRulesMatchStrongJudgeValidate() {
        fun ok(s: String) = JudgeV1.validate(s, truncated = false)
        // a raw output of the PoC session (q01-g)
        assertEquals(JudgeResult.Label(2), ok("{\n\"reason\": \"과거와 현재 사이에 연결이 명확하다.\",\n\"label\": 2\n}"))
        assertEquals(JudgeResult.Label(0), ok("""{"reason":"r","label":0,"extra":true}"""))
        listOf(
            """{"reason":"r","label":2""",     // malformed JSON
            """[2]""",                          // not an object
            """{"reason":"r"}""",               // no label
            """{"reason":"r","label":3}""",     // out of range
            """{"reason":"r","label":-1}""",
            """{"reason":"r","label":2.0}""",   // float (Python: not int)
            """{"reason":"r","label":"2"}""",   // string
            """{"reason":"r","label":true}""",  // bool (Python: excluded)
            """{"reason":"  ","label":2}""",    // empty reason
            """{"reason":2,"label":2}""",       // reason not a string
            """{"label":1}""",
        ).forEach { assertTrue(it, ok(it) is JudgeResult.Invalid) }
        assertTrue(JudgeV1.validate("""{"reason":"r","label":2}""", truncated = true) is JudgeResult.Invalid)
    }

    private class FakeEngine(var answer: () -> ChatCompletion) : QwenEngine {
        val requests = mutableListOf<Triple<String, String, String>>()
        var closed = false
        override fun complete(system: String, user: String, jsonSchema: String): ChatCompletion {
            requests += Triple(system, user, jsonSchema)
            return answer()
        }
        override fun close() { closed = true }
    }

    @Test
    fun judgeSendsJudgeV1AndMapsTheFinish() = runTest {
        val engine = FakeEngine { ChatCompletion("""{"reason":"이어짐","label":2}""", Finish.STOP) }
        val judge = QwenJudge(engine, JudgeV1.parse(asset))
        assertEquals(JudgeResult.Label(2), judge.judge("지금", "예전"))
        val (system, user, schema) = engine.requests.single()
        assertEquals(JudgeV1.parse(asset).system, system)
        assertTrue(user.contains("지금") && user.contains("예전"))
        assertEquals(JudgeV1.SCHEMA_JSON, schema)

        engine.answer = { ChatCompletion("""{"reason":"이어짐","label":2}""", Finish.LENGTH) }
        assertTrue(judge.judge("a", "b") is JudgeResult.Invalid) // truncated
        engine.answer = { ChatCompletion("", Finish.CONTEXT) }
        assertTrue(judge.judge("a", "b") is JudgeResult.Invalid) // too long to judge: a failed pair, not a runtime error
    }

    @Test
    fun nativeErrorsAreRuntimeErrorsNotInvalidJudgments() = runTest {
        val judge = QwenJudge(FakeEngine { throw RuntimeException("qwen complete: llama_decode failed") }, JudgeV1.parse(asset))
        try {
            judge.judge("a", "b")
            fail("expected the runtime error to propagate (analysis FAILED → retried)")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("llama_decode"))
        }
    }

    @Test
    fun modelIdNamesEverythingThatDecidesALabel() {
        val id = QwenJudge.MODEL_ID
        listOf("qwen3.5-2b-q4_k_m@20cb277f0967", "judge_v1@553ab6176c0293f9", "llama.cpp-b10456", "t0-s7-c2048-n192-nothink").forEach {
            assertTrue("$it in $id", it in id)
        }
    }

    @Test
    fun cpuFeatureCheckMatchesThePocBuildFlags() {
        assertTrue(QwenRuntimeSupport.cpuSupports("Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp sha512 sve i8mm bf16 sme"))
        assertFalse(QwenRuntimeSupport.cpuSupports("Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp asimddp")) // no i8mm
        assertFalse(QwenRuntimeSupport.cpuSupports(""))
    }
}
