package app.oneulmundeuk.related

import app.oneulmundeuk.data.db.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

/** What the runtime needs beyond `RelatedAnalyzer`: queue recovery after a process death and the retry of failures. */
interface RelatedRuntimeStorage : RelatedAnalysisStorage {
    /** RUNNING rows left by a killed process → PENDING (their cached embeddings / judgments are reused). */
    suspend fun resetRunningToPending(now: Long): Int

    /** FAILED rows with attempts < [maxAttempts] → PENDING, attempts kept (design §4: at most 3 tries per version). */
    suspend fun requeueFailed(maxAttempts: Int, now: Long): Int
}

class RoomRelatedRuntimeStorage(database: () -> AppDatabase) : RoomRelatedAnalysisStorage(database), RelatedRuntimeStorage {
    private val dao get() = db.relatedDao()
    override suspend fun resetRunningToPending(now: Long): Int = dao.resetRunningToPending(now)
    override suspend fun requeueFailed(maxAttempts: Int, now: Long): Int = dao.requeueFailed(maxAttempts, now)
}

/** A loaded model and how to free it (e5: ORT session ~220 MB · Qwen: llama.cpp model + context, ~2.7 GB peak). */
class LoadedModel<T>(val model: T, private val onClose: () -> Unit) : Closeable {
    override fun close() = onClose()
}

/** Opens a model (seconds, off the main thread). Throws when files are missing / invalid or the runtime is unavailable. */
fun interface ModelLoader<T> {
    suspend fun load(): LoadedModel<T>
}

/** One drain pass (tests / logs). */
data class DrainResult(val outcomes: List<AnalysisOutcome>, val embedderLoaded: Boolean, val judgeLoaded: Boolean, val error: Throwable? = null)

/**
 * Production related runtime (release; debug without the fake flag file): the M5 pipeline with the real models,
 * in the app process.
 *
 *   save → `RelatedInvalidator` queues the record (PENDING) → [onRecordsChanged] → one drain pass:
 *   `RelatedAnalyzer` per PENDING target, FIFO — e5 RELATED embeddings (M6-9 cache) → earlier records → cosine Top 30
 *   → Qwen judge_v1 per pair (judgment cache) → DONE → `RelatedStore` shows label 2 (≤ 5) of [activePipelineVersion].
 *
 * - [gate] = `SemanticGate` (관련된 생각 ON AND bundle Ready) AND the native judge can run on this device. Closed →
 *   nothing is queued, loaded or inferred; stored DONE results stay visible (design §3: OFF stops analysis only).
 * - One pass at a time ([mutex]) → one Qwen / one e5 instance, never two inferences at once. Models are loaded lazily
 *   (only when a vector / judgment is actually missing) and closed at the end of every pass, also after a failure.
 * - Failures: a runtime error (load / native / storage) marks that analysis FAILED and ends the pass; the record write
 *   is never involved (it committed before). Next pass (next save / app start) re-queues FAILED with attempts < 3.
 *   PENDING / RUNNING / FAILED are never shown, so no partial result is visible.
 * - Process death: RUNNING → PENDING at [start]. Late results (after the save grace window) only appear in Detail.
 * - Explore is not connected ([textEmbedder] = null).
 */
class LocalRelatedRuntime(
    private val storage: RelatedRuntimeStorage,
    private val gate: Flow<Boolean>,
    /** Before the gate is read (app start): re-read the installed models (`ModelInstaller.refresh`). */
    private val prepare: suspend () -> Unit,
    /** `TextEmbedder.modelId` of the e5 RELATED space (known without loading). */
    private val embedderModelId: String,
    /** `RelatedValueJudge.modelId` of the judge (known without loading). */
    private val judgeModelId: String,
    private val embedderLoader: ModelLoader<TextEmbedder>,
    private val judgeLoader: ModelLoader<RelatedValueJudge>,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) : RelatedRuntime {
    @Volatile private var gateOpen = false
    private val mutex = Mutex()

    override val activePipelineVersion: String = RelatedPipeline.version(embedderModelId, judgeModelId)
    override val analysisEnabled: Boolean get() = gateOpen
    override val textEmbedder: TextEmbedder? = null

    override fun start() {
        scope.launch {
            try {
                prepare()
                storage.resetRunningToPending(now())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("related runtime start", e)
            }
            gate.distinctUntilChanged().collect { open ->
                gateOpen = open
                if (open) scope.launch { drain() }
            }
        }
    }

    override fun onRecordsChanged() {
        if (gateOpen) scope.launch { drain() }
    }

    /** One pass over the queue (serialised). Never throws except cancellation; the gate is re-checked before each target. */
    suspend fun drain(): DrainResult = mutex.withLock {
        val embedder = LazyModel(embedderLoader)
        val judge = LazyModel(judgeLoader)
        val lazyEmbedder = object : TextEmbedder {
            override val modelId: String = embedderModelId
            override suspend fun embed(text: String): FloatArray = embedder.get(embedderModelId) { it.modelId }.embed(text)
        }
        val lazyJudge = object : RelatedValueJudge {
            override val modelId: String = judgeModelId
            override suspend fun judge(current: String, past: String): JudgeResult = judge.get(judgeModelId) { it.modelId }.judge(current, past)
        }
        val outcomes = ArrayList<AnalysisOutcome>()
        try {
            if (!gateOpen) return@withLock DrainResult(outcomes, false, false)
            storage.requeueFailed(MAX_ATTEMPTS, now())
            val analyzer = RelatedAnalyzer(storage, lazyEmbedder, lazyJudge, now)
            check(analyzer.pipelineVersion == activePipelineVersion)
            for (i in 0 until MAX_RUNS_PER_PASS) {
                if (!gateOpen) break
                val outcome = analyzer.runNext() ?: break
                outcomes += outcome
                if (outcome is AnalysisOutcome.Failed) {
                    log("related analysis failed", outcome.error)
                    break // the runtime cannot work right now: stop instead of failing every queued record
                }
            }
            DrainResult(outcomes, embedder.loaded, judge.loaded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("related drain stopped", e)
            DrainResult(outcomes, embedder.loaded, judge.loaded, e)
        } finally {
            embedder.close()
            judge.close()
        }
    }

    /** Loads on first use, closes once; checks the loaded model is the one the pipeline version names. */
    private class LazyModel<T>(private val loader: ModelLoader<T>) {
        private var loadedModel: LoadedModel<T>? = null
        val loaded: Boolean get() = loadedModel != null

        suspend fun get(expectedId: String, idOf: (T) -> String): T {
            val m = loadedModel ?: loader.load().also { loadedModel = it }
            check(idOf(m.model) == expectedId) { "loaded ${idOf(m.model)}, expected $expectedId" }
            return m.model
        }

        fun close() {
            runCatching { loadedModel?.close() }
            loadedModel = null
        }
    }

    companion object {
        /** Design §4: a runtime error is retried at most this many times per text / pipeline version. */
        const val MAX_ATTEMPTS = 3

        /** Targets per pass at most (a pass normally ends when the queue is empty). */
        const val MAX_RUNS_PER_PASS = 50
    }
}
