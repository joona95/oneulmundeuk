package app.oneulmundeuk.related


/**
 * What this build can do for related records. Chosen per build type by `createRelatedRuntime` (src/debug, src/release),
 * so `main` never references a model or a fake.
 *
 * - release (M6-4): [NoRelatedRuntime] — no model yet: nothing is queued, nothing runs, nothing is shown.
 * - debug: `DebugRelatedRuntime` — deterministic fake models through the real analyzer / Room / store, behind a flag file.
 * - M6-5+: the real e5 / Qwen runtime replaces the release one (download, Settings ON/OFF, WorkManager).
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
     * Explore search e5 (null = no local model in this build / runtime). The SAME embedder Related uses (shared
     * `record_embedding` cache). Explore uses no LLM (experiments/search S1 · Thought Index PoC).
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
