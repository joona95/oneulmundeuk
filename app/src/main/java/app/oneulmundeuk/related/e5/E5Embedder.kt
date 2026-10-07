package app.oneulmundeuk.related.e5

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.oneulmundeuk.related.TextEmbedder
import app.oneulmundeuk.related.model.RelatedModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.LongBuffer

/**
 * On-device e5 (`dragonkue/multilingual-e5-small-ko-v2` @ fcfc26bf — files = `RelatedModels.E5` (INT8-embrows ONNX) +
 * `RelatedModels.E5_TOKENIZER`, experiments/e5-android).
 * The ONNX graph already does mean pooling + L2 normalization, so the output IS the sentence embedding
 * (384 floats, cosine = dot product). This class only: prefix → [XlmrTokenizer] → input_ids / attention_mask → run.
 *
 * Loaded only by the production runtime (`LocalRelatedRuntime`) after `SemanticGate` allows it.
 *
 * Prefixes are NOT one setting: the app talks to e5 through [textEmbedder] for an [E5Purpose], whose prefixes are
 * exactly the measured benchmark ones and whose [TextEmbedder.modelId] carries the purpose, so the embedding cache keeps
 * Related and Explore vectors apart.
 */
class E5Embedder private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val tokenizer: XlmrTokenizer,
) : Closeable {
    /** e5 for one benchmark setting: records and questions get [purpose]'s prefixes; its own cache space. */
    fun textEmbedder(purpose: E5Purpose): TextEmbedder = object : TextEmbedder {
        override val modelId: String = modelIdFor(purpose)
        override suspend fun embed(text: String): FloatArray = embedWithPrefix(purpose.recordPrefix, text)
        override suspend fun embedQuery(query: String): FloatArray = embedWithPrefix(purpose.queryPrefix, query)
    }

    suspend fun embedWithPrefix(prefix: String, text: String): FloatArray =
        withContext(Dispatchers.Default) { embedIds(tokenizer.encode(prefix + text)) }

    /** Runs the graph on already-tokenized ids (device parity checks). Blocking. */
    fun embedIds(ids: IntArray): FloatArray {
        val n = ids.size.toLong()
        val inputIds = LongArray(ids.size) { ids[it].toLong() }
        val mask = LongArray(ids.size) { 1L } // single text: no padding
        OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, n)).use { idsT ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, n)).use { maskT ->
                session.run(mapOf(INPUT_IDS to idsT, ATTENTION_MASK to maskT)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val out = (result[0].value as Array<FloatArray>)[0]
                    check(out.size == DIM) { "unexpected embedding size ${out.size}" }
                    return out
                }
            }
        }
    }

    fun tokenize(text: String): IntArray = tokenizer.encode(text)

    override fun close() = session.close()

    companion object {
        /** The model artifact (a different artifact version gives slightly different vectors). */
        val MODEL_ID: String = "${RelatedModels.E5.id}@${RelatedModels.E5.version}"
        const val DIM = 384
        const val QUERY = "query: "
        const val PASSAGE = "passage: "

        /** Embedding space = artifact + purpose (prefix policy). Stored in `record_embedding.model_id` and pipeline versions. */
        fun modelIdFor(purpose: E5Purpose): String = "$MODEL_ID|${purpose.key}"
        private const val INPUT_IDS = "input_ids"
        private const val ATTENTION_MASK = "attention_mask"

        /** Loads both artifacts (blocking, seconds — call off the main thread). Throws if either is missing / invalid. */
        fun load(modelFile: File, tokenizerFile: File): E5Embedder {
            val tokenizer = tokenizerFile.bufferedReader(Charsets.UTF_8).useLines { XlmrTokenizer.load(it) }
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // Default MLAS kernels only: KleidiAI SME2 kernels crashed (SIGILL) on an SME-without-SME2 CPU
                // (SM8850, ORT issue #26377). Also the same kernels the host parity run used.
                addConfigEntry("mlas.disable_kleidiai", "1")
            }
            val session = env.createSession(modelFile.absolutePath, options)
            return E5Embedder(env, session, tokenizer)
        }
    }
}
