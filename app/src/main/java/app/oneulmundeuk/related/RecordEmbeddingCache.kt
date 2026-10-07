package app.oneulmundeuk.related

import app.oneulmundeuk.data.db.RecordEmbeddingEntity
import app.oneulmundeuk.data.db.RecordTextRow

/** The `record_embedding` table as the embedding cache sees it: one row per (record, embedding space). */
interface EmbeddingStore {
    /** The row of [recordId] in the space [modelId] (= `TextEmbedder.modelId`), or null. */
    suspend fun embedding(recordId: String, modelId: String): RecordEmbeddingEntity?
    suspend fun saveEmbedding(embedding: RecordEmbeddingEntity)
}

/**
 * The one record-embedding cache rule, shared by `RelatedAnalyzer`, Explore search and the e5 embedding step:
 * a stored vector is reused only when it was made in the same space ([TextEmbedder.modelId] = model artifact version +
 * purpose / prefix policy — part of the key, so another model or purpose is never even read), for the same text version
 * ([RelatedText.hash]) and is well-formed. Anything else → [embedder] runs and the row is replaced.
 * Exceptions from [embedder] / [store] propagate: the caller decides what an embedding failure means.
 */
class RecordEmbeddingCache(
    private val store: EmbeddingStore,
    private val embedder: TextEmbedder,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val modelId: String get() = embedder.modelId

    /** Current stored vector of [record] for this space, or null (missing / stale / malformed). Never runs a model. */
    suspend fun cached(record: RecordTextRow, textHash: String = RelatedText.hash(record.text)): FloatArray? {
        val stored = store.embedding(record.id, embedder.modelId) ?: return null
        val valid = stored.modelId == embedder.modelId && stored.textHash == textHash && stored.dim > 0 && stored.vector.size == stored.dim * 4
        return if (valid) EmbeddingCodec.decode(stored.vector, stored.dim) else null
    }

    /** Cached vector, or embed + store. [onMiss] is told when the model ran (stats). */
    suspend fun vectorFor(
        record: RecordTextRow,
        textHash: String = RelatedText.hash(record.text),
        onMiss: () -> Unit = {},
        onHit: () -> Unit = {},
    ): FloatArray {
        cached(record, textHash)?.let { onHit(); return it }
        onMiss()
        val vector = embedder.embed(record.text)
        check(vector.isNotEmpty()) { "empty embedding" }
        store.saveEmbedding(RecordEmbeddingEntity(record.id, embedder.modelId, textHash, vector.size, EmbeddingCodec.encode(vector), now()))
        return vector
    }
}
