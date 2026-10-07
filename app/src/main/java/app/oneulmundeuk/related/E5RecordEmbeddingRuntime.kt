package app.oneulmundeuk.related

import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.RecordEmbeddingEntity
import app.oneulmundeuk.data.db.RecordTextRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

/** What the e5 embedding step reads / writes: the analysis queue (read only) + records + the embedding table. */
interface RecordEmbeddingStorage : EmbeddingStore {
    /** PENDING analyses, first queued first. Reading never changes the queue. */
    suspend fun pendingIds(limit: Int): List<String>
    suspend fun record(recordId: String): RecordTextRow?
}

class RoomRecordEmbeddingStorage(private val database: () -> AppDatabase) : RecordEmbeddingStorage {
    private val dao get() = database().relatedDao()
    override suspend fun pendingIds(limit: Int): List<String> = dao.pendingIds(limit)
    override suspend fun record(recordId: String): RecordTextRow? = dao.recordText(recordId)
    override suspend fun embedding(recordId: String, modelId: String): RecordEmbeddingEntity? = dao.embedding(recordId, modelId)
    override suspend fun saveEmbedding(embedding: RecordEmbeddingEntity) = dao.upsertEmbedding(embedding)
}

/** A loaded model for the Related embedding space; [close] frees it (e5: the ORT session, ~220 MB). */
class LoadedEmbedder(val embedder: TextEmbedder, private val onClose: () -> Unit) : Closeable {
    override fun close() = onClose()
}

/** Opens the model (seconds, off the main thread). Throws when the files are missing / invalid. */
fun interface EmbedderLoader {
    suspend fun load(): LoadedEmbedder
}

/** One embedding pass over the queue (tests / logs). */
data class EmbeddingPass(val embedded: Int, val reused: Int, val loaded: Boolean, val error: Throwable? = null)

/**
 * Production related runtime, M6-9 step 1: real e5 record embeddings, nothing else yet.
 *
 * - [gate] = `SemanticGate` (관련된 생각 ON AND model bundle Ready). Closed → nothing is queued, no model is loaded,
 *   nothing is inferred. Today the gate cannot open in a shipped build (no model host → never Ready).
 * - Open → new / edited records are queued as PENDING analyses by `RelatedInvalidator` (the existing durable queue =
 *   "records that are analysis targets"), and each change / gate opening runs one pass: for every PENDING target its
 *   Related-space embedding ([relatedModelId], e5 `query: `) is made unless a current one is cached.
 * - The queue itself is not advanced: PENDING stays PENDING until the Qwen judge step (M6-7) runs `RelatedAnalyzer`,
 *   which then finds these vectors in the same cache. So nothing is shown ([activePipelineVersion] = null).
 * - The model is loaded lazily (only when a vector is missing) and closed at the end of the pass — e5 holds ~220 MB.
 * - Failures stay here: the record write already committed (`RecordRepository` never waits for this), a failed load /
 *   inference only ends the pass (logged); nothing is marked FAILED and the next change or app start tries again.
 * - Explore is not connected yet ([textEmbedder] = null): its e5 space (`E5Purpose.EXPLORE`) is a separate cache.
 */
class E5RecordEmbeddingRuntime(
    private val storage: RecordEmbeddingStorage,
    private val gate: Flow<Boolean>,
    /** Before the gate is read (app start): re-read the installed models (`ModelInstaller.refresh`). */
    private val prepare: suspend () -> Unit,
    /** `TextEmbedder.modelId` of the Related space — known without loading, so cache hits never load the model. */
    private val relatedModelId: String,
    private val loader: EmbedderLoader,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) : RelatedRuntime {
    @Volatile private var gateOpen = false
    private val mutex = Mutex()

    override val activePipelineVersion: String? = null
    override val analysisEnabled: Boolean get() = gateOpen
    override val textEmbedder: TextEmbedder? = null

    override fun start() {
        scope.launch {
            runCatching { prepare() }.onFailure { if (it is CancellationException) throw it; log("model state refresh failed", it) }
            gate.distinctUntilChanged().collect { open ->
                gateOpen = open
                if (open) scope.launch { embedPending() }
            }
        }
    }

    override fun onRecordsChanged() {
        if (gateOpen) scope.launch { embedPending() }
    }

    /** One pass (serialised). Never throws except cancellation; the gate is re-checked before every record. */
    suspend fun embedPending(): EmbeddingPass = mutex.withLock {
        var loaded: LoadedEmbedder? = null
        var embedded = 0 // counted only once stored
        var reused = 0
        val lazyModel = object : TextEmbedder {
            override val modelId: String = relatedModelId
            override suspend fun embed(text: String): FloatArray {
                val model = loaded ?: loader.load().also { loaded = it }
                check(model.embedder.modelId == relatedModelId) { "loaded ${model.embedder.modelId}, expected $relatedModelId" }
                return model.embedder.embed(text)
            }
        }
        val cache = RecordEmbeddingCache(storage, lazyModel, now)
        try {
            for (id in storage.pendingIds(MAX_PER_PASS)) {
                if (!gateOpen) break
                val record = storage.record(id) ?: continue // deleted meanwhile
                var hit = false
                cache.vectorFor(record, onHit = { hit = true })
                if (hit) reused++ else embedded++
            }
            EmbeddingPass(embedded, reused, loaded != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("record embedding pass stopped", e)
            EmbeddingPass(embedded, reused, loaded != null, e)
        } finally {
            runCatching { loaded?.close() }
        }
    }

    companion object {
        /** Upper bound per pass (cache hits are cheap DB reads; misses ≈ 10–200 ms each on device). */
        const val MAX_PER_PASS = 200
    }
}
