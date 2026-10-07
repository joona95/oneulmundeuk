package app.oneulmundeuk.related


/**
 * What this build can do for related records. Chosen per build type by `createRelatedRuntime` (src/debug, src/release),
 * so `main` never references a model or a fake.
 *
 * - release (M6-7/9): `productionRelatedRuntime` → [LocalRelatedRuntime] — behind `SemanticGate`, the real pipeline
 *   (e5 RELATED + Qwen judge_v1, in-process, serialised). Gate closed = nothing queued / loaded / run.
 * - debug: the same, or with a flag file `DebugRelatedRuntime` — deterministic fake models through the real analyzer /
 *   Room / store.
 * - [NoRelatedRuntime]: does nothing at all (tests).
 * - Next: WorkManager worker (execution budget · thermal), first-activation backfill, Explore e5 (`E5Purpose.EXPLORE`).
 */
interface RelatedRuntime {
    /** Version whose results are shown (`RelatedStore`). Null = show nothing. */
    val activePipelineVersion: String?

    /** Whether new / edited records are queued for analysis (`RelatedInvalidator`). */
    val analysisEnabled: Boolean

    /** App start (main thread): may resume / start background work. Must return immediately. */
    fun start()

    /** After a record write and its invalidation committed (may have queued work). Must return immediately. */
    fun onRecordsChanged()

    /**
     * Explore search e5 (null = no local model in this build / runtime). Its own embedding space (`TextEmbedder.modelId`):
     * the `record_embedding` table is shared, rows are per space. Explore uses no LLM (experiments/search S1 · Thought Index PoC).
     */
    val textEmbedder: TextEmbedder?
}

object NoRelatedRuntime : RelatedRuntime {
    override val activePipelineVersion: String? = null
    override val analysisEnabled: Boolean = false
    override fun start() = Unit
    override fun onRecordsChanged() = Unit
    override val textEmbedder: TextEmbedder? = null
}
