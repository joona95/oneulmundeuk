package app.oneulmundeuk.related.model

/**
 * The on-device models behind 관련된 생각 (e5 model + its tokenizer + Qwen) — installed and judged together as ONE bundle
 * (all verified = Ready).
 * Values come only from what the repo has already fixed (docs/m5-related-decision.md, docs/m6-related-design.md,
 * experiments/android-qwen-poc/README.md). Anything not decided yet stays null / [ModelSource.Unconfigured] — never a
 * guessed production URL, size or hash. An artifact with a null value can never be downloaded or become Ready.
 */
enum class ModelRole { EMBEDDING, JUDGE }

sealed interface ModelSource {
    /** No host decided yet (docs/m6-related-design.md §8: "제공자 미정"). Download start fails safely. */
    data object Unconfigured : ModelSource

    /** Static HTTPS file, immutable per version, Range supported, no auth / cookies / user identifiers. */
    data class Https(val url: String) : ModelSource
}

data class ModelArtifact(
    val id: String,
    val role: ModelRole,
    /** File format on device (informational; the runtime step decides how to load it). */
    val format: String,
    /** Part of the install path (`models/<id>/<version>/`): a new version never reuses an old file as Ready. */
    val version: String,
    val fileName: String?,
    val expectedBytes: Long?,
    /** Lower-case hex. Fixed in the app — a value served by the host is never trusted. */
    val sha256: String?,
    val source: ModelSource,
) {
    /** Everything needed to download and verify it. */
    val isConfigured: Boolean
        get() = fileName != null && expectedBytes != null && sha256 != null && source is ModelSource.Https
}

object RelatedModels {
    /**
     * e5 = `dragonkue/multilingual-e5-small-ko-v2` @ `fcfc26bf3558` (M5 benchmark model) as the M6-8 Android artifact
     * (experiments/e5-android/README.md): ONNX opset 17, `input_ids` + `attention_mask` → `sentence_embedding` [1, 384]
     * (mean pooling + L2 normalize inside the graph). Only the word-embedding table is INT8 (one scale per row); the
     * encoder stays FP32. Chosen over the FP32 export (470,234,464 bytes, kept as the experiment's reference only):
     * 39 % of the size, RSS +223 MB instead of +492 MB on the device, host parity cosine ≥ 0.99995, M5 Related Top-5
     * 17/17, S1 Explore Top-10 sets 27/27. Host still undecided → [ModelSource.Unconfigured].
     */
    val E5 = ModelArtifact(
        id = "multilingual-e5-small-ko-v2",
        role = ModelRole.EMBEDDING,
        format = "onnx",
        version = "fcfc26bf-int8-embrows-ab2d3fa7",
        fileName = "e5-small-ko-v2-fcfc26bf.int8-embrows.onnx",
        expectedBytes = 183_192_536L,
        sha256 = "ab2d3fa70720f6026106572b729606f1ad33257538984abe0fd262c81855e04a",
        source = ModelSource.Unconfigured,
    )

    /**
     * Tokenizer of [E5] for the pure-Kotlin `related.e5.XlmrTokenizer` (XLM-R Unigram, made by
     * experiments/e5-android/make_tokenizer_artifact.py from the same fcfc26bf snapshot). A separate artifact (own id
     * → own directory) because one [ModelArtifact] is one file; e5 is usable only when both are Ready.
     */
    val E5_TOKENIZER = ModelArtifact(
        id = "multilingual-e5-small-ko-v2-tokenizer",
        role = ModelRole.EMBEDDING,
        format = "xlmr-unigram-txt",
        version = "fcfc26bf-6aab11c2",
        fileName = "e5-small-ko-v2-tokenizer.txt",
        expectedBytes = 8_080_014L,
        sha256 = "6aab11c24ea1ecb59cf74fc8fc0f09f03129e3df249aa2aba47f13ed78cca893",
        source = ModelSource.Unconfigured,
    )

    /**
     * Qwen3.5-2B Q4_K_M GGUF — the exact file verified in the Android PoC (llama.cpp b10456, = Ollama `qwen3.5:2b-q4_K_M`
     * blob): experiments/android-qwen-poc/README.md. Host still undecided → [ModelSource.Unconfigured].
     */
    val QWEN = ModelArtifact(
        id = "qwen3.5-2b-q4_k_m",
        role = ModelRole.JUDGE,
        format = "gguf",
        version = "20cb277f0967",
        fileName = "qwen3.5-2b-q4_K_M.gguf",
        expectedBytes = 1_274_396_992L,
        sha256 = "20cb277f0967ace47b0b5d5658e5e494a88937f7378b74b5a266496400938f4c",
        source = ModelSource.Unconfigured,
    )

    val BUNDLE: List<ModelArtifact> = listOf(E5, E5_TOKENIZER, QWEN)
}

/**
 * The one place a production semantic runtime asks "may I run inference?": 관련된 생각 ON AND the model bundle Ready.
 * Today nothing real calls it — release keeps NoRelatedRuntime, debug keeps its flag-file fake (a test tool, not a
 * model). TODO(M6-7/8/9): the real runtime and the analysis worker must check this before loading a model.
 */
object SemanticGate {
    fun allows(relatedEnabled: Boolean, install: ModelInstallState): Boolean =
        relatedEnabled && install is ModelInstallState.Ready
}
