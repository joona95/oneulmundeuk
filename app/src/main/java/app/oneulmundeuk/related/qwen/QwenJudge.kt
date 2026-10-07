package app.oneulmundeuk.related.qwen

import app.oneulmundeuk.related.JudgeResult
import app.oneulmundeuk.related.RelatedValueJudge
import app.oneulmundeuk.related.model.RelatedModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/** How a completion ended (llama-server finish_reason + its context check). */
enum class Finish {
    /** EOS / stop string. */
    STOP,

    /** [QwenGeneration.N_PREDICT] tokens without an end → truncated (PoC `finish_reason == "length"`). */
    LENGTH,

    /** Prompt + output do not fit [QwenGeneration.N_CTX] (two very long records): this pair cannot be judged. */
    CONTEXT,
}

class ChatCompletion(val content: String, val finish: Finish)

/** One chat completion with a JSON schema, under the PoC generation settings. Not thread-safe by contract; close once. */
interface QwenEngine : Closeable {
    /** Blocking (seconds). Throws when the runtime cannot run (native error) — that is NOT an invalid judgment. */
    fun complete(system: String, user: String, jsonSchema: String): ChatCompletion
}

/**
 * The PoC llama-server settings (experiments/android-qwen-poc/README.md "최종 결과"): `-c 2048 -n 192 --temp 0 --seed 7
 * -np 1 --jinja`, request `temperature 0 · seed 7 · max_tokens 192 · response_format json_schema ·
 * chat_template_kwargs.enable_thinking=false · cache_prompt`. The native side applies them the way the server does.
 */
object QwenGeneration {
    const val LLAMA_CPP = "b10456" // = third_party/llama.cpp submodule (f275595dd16f)
    const val N_CTX = 2048
    const val N_PREDICT = 192
    const val SEED = 7
}

/**
 * judge_v1 on local Qwen3.5-2B ([RelatedValueJudge]). [modelId] = everything that decides a label: GGUF (artifact
 * version = sha256 prefix), prompt sha, runtime version and generation settings — part of the pipeline version, so a
 * change in any of them re-judges instead of reusing old labels.
 */
class QwenJudge(private val engine: QwenEngine, private val prompt: JudgeV1.Prompt) : RelatedValueJudge {
    override val modelId: String = MODEL_ID

    override suspend fun judge(current: String, past: String): JudgeResult = withContext(Dispatchers.Default) {
        val out = engine.complete(prompt.system, prompt.user(current, past), JudgeV1.SCHEMA_JSON)
        when (out.finish) {
            Finish.CONTEXT -> JudgeResult.Invalid("prompt longer than the context")
            Finish.LENGTH -> JudgeV1.validate(out.content, truncated = true)
            Finish.STOP -> JudgeV1.validate(out.content, truncated = false)
        }
    }

    companion object {
        val MODEL_ID: String = "${RelatedModels.QWEN.id}@${RelatedModels.QWEN.version}" +
            "|judge_v1@${JudgeV1.PROMPT_SHA256.take(16)}" +
            "|llama.cpp-${QwenGeneration.LLAMA_CPP}" +
            "|t0-s${QwenGeneration.SEED}-c${QwenGeneration.N_CTX}-n${QwenGeneration.N_PREDICT}-nothink-json"
    }
}
