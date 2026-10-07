package app.oneulmundeuk

import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.RecordEmbeddingEntity
import app.oneulmundeuk.data.db.RecordTextRow
import app.oneulmundeuk.data.db.RelatedAnalysisEntity
import app.oneulmundeuk.data.db.RelatedJudgmentEntity
import app.oneulmundeuk.related.JudgeResult
import app.oneulmundeuk.related.RelatedAnalysisStorage
import app.oneulmundeuk.related.RelatedText
import app.oneulmundeuk.related.RelatedValueJudge
import app.oneulmundeuk.related.TextEmbedder

/** Deterministic embedder: the vector comes from [vectors] (by text). Counts calls; can be told to fail. */
class FakeEmbedder(
    override val modelId: String = "fake-e5",
    var vectors: (String) -> FloatArray,
) : TextEmbedder {
    val calls = mutableListOf<String>()
    var failOnCall: Int? = null

    override suspend fun embed(text: String): FloatArray {
        calls += text
        if (failOnCall == calls.size) throw IllegalStateException("embedder crashed")
        return vectors(text)
    }
}

/** Deterministic judge: [answer] by past text. Counts calls; can throw on the n-th call or run a hook. */
class FakeJudge(
    override val modelId: String = "fake-qwen",
    var answer: (current: String, past: String) -> JudgeResult,
) : RelatedValueJudge {
    val calls = mutableListOf<Pair<String, String>>()
    var failOnCall: Int? = null
    var beforeAnswer: suspend (Int) -> Unit = {}

    override suspend fun judge(current: String, past: String): JudgeResult {
        calls += current to past
        beforeAnswer(calls.size)
        if (failOnCall == calls.size) throw IllegalStateException("judge OOM")
        return answer(current, past)
    }
}

/** Map-backed storage with the same guards as the Room DAO (PENDING → RUNNING → DONE / FAILED, prune on DONE). */
class FakeAnalysisStorage : RelatedAnalysisStorage {
    val records = linkedMapOf<String, RecordTextRow>()
    val analyses = linkedMapOf<String, RelatedAnalysisEntity>()
    /** Keyed like the v4 table: (record_id, model_id). */
    val embeddings = linkedMapOf<Pair<String, String>, RecordEmbeddingEntity>()
    val judgmentRows = linkedMapOf<Pair<String, String>, RelatedJudgmentEntity>()
    private var clock = 0L

    fun add(id: String, text: String, createdAt: Long) { records[id] = RecordTextRow(id, text, createdAt) }

    fun enqueue(id: String) {
        val r = records.getValue(id)
        val existing = analyses[id]
        analyses[id] = RelatedAnalysisEntity(
            id, AnalysisStatus.PENDING, null, RelatedText.hash(r.text), 0, null,
            if (existing?.status == AnalysisStatus.PENDING) existing.queuedAt else ++clock, clock, existing?.completedAt, null,
        )
    }

    fun edit(id: String, text: String) { records[id] = records.getValue(id).copy(text = text) }

    fun delete(id: String) {
        records.remove(id); analyses.remove(id); embeddings.keys.removeAll { it.first == id }
        judgmentRows.keys.removeAll { it.first == id || it.second == id }
    }

    fun status(id: String) = analyses[id]?.status

    override suspend fun nextPendingId(): String? =
        analyses.values.filter { it.status == AnalysisStatus.PENDING }.minWithOrNull(compareBy({ it.queuedAt }, { it.recordId }))?.recordId

    override suspend fun analysis(recordId: String) = analyses[recordId]
    override suspend fun record(recordId: String) = records[recordId]
    override suspend fun recordsBefore(target: RecordTextRow) =
        records.values.filter { it.createdAt < target.createdAt && it.id != target.id }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    override suspend fun embedding(recordId: String, modelId: String) = embeddings[recordId to modelId]
    override suspend fun saveEmbedding(embedding: RecordEmbeddingEntity) { embeddings[embedding.recordId to embedding.modelId] = embedding }
    override suspend fun judgments(targetId: String) = judgmentRows.values.filter { it.targetId == targetId }
    override suspend fun saveJudgment(judgment: RelatedJudgmentEntity) {
        check(judgment.targetId in records && judgment.candidateId in records) { "FK" }
        judgmentRows[judgment.targetId to judgment.candidateId] = judgment
    }

    override suspend fun start(recordId: String, pipelineVersion: String, textHash: String, now: Long): Boolean {
        val a = analyses[recordId] ?: return false
        if (a.status != AnalysisStatus.PENDING) return false
        analyses[recordId] = a.copy(status = AnalysisStatus.RUNNING, pipelineVersion = pipelineVersion, textHash = textHash)
        return true
    }

    override suspend fun complete(recordId: String, pipelineVersion: String, textHash: String, usedCandidateIds: Set<String>, now: Long): Boolean {
        val a = analyses[recordId] ?: return false
        if (a.status != AnalysisStatus.RUNNING || a.pipelineVersion != pipelineVersion || a.textHash != textHash) return false
        analyses[recordId] = a.copy(status = AnalysisStatus.DONE, completedAt = now, error = null)
        judgmentRows.values.removeAll {
            it.targetId == recordId && (it.pipelineVersion != pipelineVersion || it.targetHash != textHash || it.candidateId !in usedCandidateIds)
        }
        return true
    }

    override suspend fun fail(recordId: String, error: String, now: Long): Boolean {
        val a = analyses[recordId] ?: return false
        if (a.status != AnalysisStatus.RUNNING) return false
        analyses[recordId] = a.copy(status = AnalysisStatus.FAILED, attempts = a.attempts + 1, error = error)
        return true
    }
}
