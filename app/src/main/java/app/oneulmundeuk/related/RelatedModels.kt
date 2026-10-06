package app.oneulmundeuk.related

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * Model boundaries of the related pipeline (docs/m6-related-design.md §2). The pipeline only sees these types;
 * runtime details (ONNX / llama.cpp / JNI / prompt / "query: " prefix / JSON schema) stay inside implementations
 * that M6-7 / M6-8 plug in. M6-3 uses deterministic fakes only.
 */

/** e5 role: text → embedding. [modelId] identifies the model + preprocessing; changing it invalidates embeddings. */
interface TextEmbedder {
    val modelId: String

    /** Throwing = the embedder cannot run (runtime error): the whole analysis fails and is retried later. */
    suspend fun embed(text: String): FloatArray

    /**
     * A search question (Explore), compared against [embed]ded records. e5 implementations add their "query: " prefix
     * here ("passage: " / record text in [embed]); a model without roles can keep this default.
     */
    suspend fun embedQuery(query: String): FloatArray = embed(query)
}

/**
 * judge_v1 role: (current text, past text) → label 0 / 1 / 2. Nothing else is given (no dates, emotion, category).
 * [modelId] identifies model + prompt (sha) + decoding; changing it invalidates judgments.
 * Return [JudgeResult.Invalid] for a bad output (format error, truncated, label out of range): that pair is FAILED and
 * not retried for this pipeline version (greedy decoding gives the same answer — M5 "1 attempt").
 * Throw only when the model cannot run at all (load failure, OOM, JNI error).
 */
interface RelatedValueJudge {
    val modelId: String

    suspend fun judge(current: String, past: String): JudgeResult
}

sealed interface JudgeResult {
    data class Label(val value: Int) : JudgeResult
    data class Invalid(val reason: String) : JudgeResult
}

/** Version of everything that decides a result. Stored on analyses / judgments; any change re-analyses lazily. */
object RelatedPipeline {
    /** Bump when the M5 policy in [RelatedPolicy] / [selectWorthShowing] / [rankCandidates] changes. */
    const val POLICY = "m5-label2-top30-max5"

    fun version(embedder: TextEmbedder, judge: RelatedValueJudge): String =
        "$POLICY|e=${embedder.modelId}|j=${judge.modelId}|${RelatedText.VERSION}"
}

/** `record_embedding.vector`: float32 little-endian, [FloatArray.size] × 4 bytes. Model-agnostic (any dimension). */
object EmbeddingCodec {
    fun encode(vector: FloatArray): ByteArray =
        ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { vector.forEach(::putFloat) }.array()

    fun decode(bytes: ByteArray, dim: Int): FloatArray {
        require(bytes.size == dim * 4) { "embedding blob ${bytes.size} bytes != $dim × 4" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(dim) { buffer.getFloat() }
    }
}
