package app.placeholder.journal.search

import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.data.db.RecordEmbeddingEntity
import app.placeholder.journal.data.db.RecordTextRow
import app.placeholder.journal.related.EmbeddingCodec
import app.placeholder.journal.related.RelatedText
import app.placeholder.journal.related.TextEmbedder
import app.placeholder.journal.related.rankCandidates
import app.placeholder.journal.util.TimeFormat
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId

object SearchPolicy {
    /** Semantic results shown at most (S1 / S2 evaluated Top 10). No similarity threshold yet (no-result is a TODO). */
    const val MAX_RESULTS = 10

    /** Pure date questions ("지난달에 무슨 일이 있었지?") list the period's records, at most this many. */
    const val MAX_DATE_RESULTS = 50

    /** Category suggestion: this many records of that category (e5 order) come first, then the global e5 order fills up. */
    const val CATEGORY_HINT_TOP = 3
}

/**
 * Pure: category-hint ordering. [ranked] = every candidate id by e5 similarity (best first). The category's best
 * [SearchPolicy.CATEGORY_HINT_TOP] come first, then the global order without duplicates, cut at [limit].
 * No score adjustment, no strict filter: records outside the category keep their global place after the head.
 */
fun withCategoryHint(ranked: List<String>, inCategory: Set<String>, limit: Int = SearchPolicy.MAX_RESULTS): List<String> {
    val head = ranked.filter { it in inCategory }.take(SearchPolicy.CATEGORY_HINT_TOP)
    return (head + ranked.filterNot { it in head }).take(limit)
}

/** What search needs from storage: every record + the shared embedding cache (same table as Related). */
interface SearchStorage {
    suspend fun allRecords(): List<RecordTextRow>
    suspend fun recordIdsInCategory(categoryId: String): List<String>
    suspend fun embedding(recordId: String): RecordEmbeddingEntity?
    suspend fun saveEmbedding(embedding: RecordEmbeddingEntity)
}

class RoomSearchStorage(private val db: AppDatabase) : SearchStorage {
    private val dao get() = db.relatedDao()
    override suspend fun allRecords() = dao.allRecordTexts()
    override suspend fun recordIdsInCategory(categoryId: String) = dao.recordIdsInCategory(categoryId)
    override suspend fun embedding(recordId: String) = dao.embedding(recordId)
    override suspend fun saveEmbedding(embedding: RecordEmbeddingEntity) = dao.upsertEmbedding(embedding)
}

/**
 * Explore search engine — e5 only, no LLM judge (experiments/search S1: a pairwise Qwen SearchJudge was worse than
 * e5 alone, so it is not used). Routing by [ExploreQuery]:
 *  - Semantic: question → e5 query embedding → cosine against every record (shared embedding cache) → Top [SearchPolicy.MAX_RESULTS]
 *  - TemporalSemantic: the same over records inside the date range only, using the question without its date words
 *  - PureTemporal: the date range's records in date order — needs no model, so it works even when [embedder] is null
 *  - categoryHint (app category suggestion only, Semantic route only): [withCategoryHint] — the category's e5 Top 3 first,
 *    then the global e5 order fills the rest (no duplicates, no score boost, no strict filter). Typed text: no hint.
 * [embedder] null (no local model) → [SemanticSearchResult.Unavailable] for anything that needs e5. Runtime error → Unavailable.
 * Never keyword / LIKE matching.
 */
class ExploreSearch(
    private val storage: SearchStorage,
    private val embedder: TextEmbedder?,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
    private val now: () -> Long = System::currentTimeMillis,
) : SemanticSearch {

    override suspend fun search(query: String, categoryHint: String?): SemanticSearchResult {
        val question = RelatedText.normalize(query)
        if (question.isEmpty()) return SemanticSearchResult.Found(emptyList())
        return try {
            when (val plan = ExploreQuery.of(question, today())) {
                is ExploreQuery.PureTemporal -> {
                    val inside = storage.allRecords().filter { dateOf(it) in plan.range }
                    val ordered = TemporalParser.dateOrder(plan.range, inside, ::dateOf, { it.createdAt }, { it.id })
                    SemanticSearchResult.Found(ordered.take(SearchPolicy.MAX_DATE_RESULTS).map { it.id })
                }
                is ExploreQuery.TemporalSemantic -> rank(plan.text) { dateOf(it) in plan.range }
                is ExploreQuery.Semantic -> rank(plan.text, categoryHint) { true }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SemanticSearchResult.Unavailable
        }
    }

    private suspend fun rank(text: String, categoryHint: String? = null, keep: (RecordTextRow) -> Boolean): SemanticSearchResult {
        val embedder = embedder ?: return SemanticSearchResult.Unavailable
        val records = storage.allRecords().filter(keep)
        if (records.isEmpty()) return SemanticSearchResult.Found(emptyList())
        val queryVector = embedder.embedQuery(text)
        val ranked = rankCandidates(queryVector, records.map { it.id to vectorFor(embedder, it) }, records.size).map { it.recordId }
        if (categoryHint == null) return SemanticSearchResult.Found(ranked.take(SearchPolicy.MAX_RESULTS))
        return SemanticSearchResult.Found(withCategoryHint(ranked, storage.recordIdsInCategory(categoryHint).toSet()))
    }

    private fun dateOf(r: RecordTextRow): LocalDate = TimeFormat.dayKey(r.createdAt, zone)

    /** Same embedding cache and validity rule as `RelatedAnalyzer` (model id + text version + size). */
    private suspend fun vectorFor(embedder: TextEmbedder, record: RecordTextRow): FloatArray {
        val hash = RelatedText.hash(record.text)
        val stored = storage.embedding(record.id)
        if (stored != null && stored.modelId == embedder.modelId && stored.textHash == hash && stored.vector.size == stored.dim * 4) {
            return EmbeddingCodec.decode(stored.vector, stored.dim)
        }
        val vector = embedder.embed(record.text)
        storage.saveEmbedding(RecordEmbeddingEntity(record.id, embedder.modelId, hash, vector.size, EmbeddingCodec.encode(vector), now()))
        return vector
    }
}
