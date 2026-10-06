package app.oneulmundeuk.related

import kotlin.math.sqrt

/**
 * Pure rules of the M5 policy used by the M6 pipeline (`RelatedAnalyzer`):
 *   e5 Top [CANDIDATE_LIMIT] → judge_v1 label per candidate → label 2 only → e5 similarity DESC → at most [MAX_RESULTS].
 * No runtime, storage or Android dependency here.
 */
object RelatedPolicy {
    /** e5 candidates handed to the judge (all earlier records when fewer). */
    const val CANDIDATE_LIMIT = 30

    /** Shown at most (never padded; 0 is a normal answer). */
    const val MAX_RESULTS = 5

    /** judge_v1: 2 = worth showing now. The only label that is ever shown. */
    const val LABEL_WORTH_SHOWING = 2

    /** judge_v1 labels; anything else (or no label) is a failed judgment. */
    fun isValidLabel(label: Int?): Boolean = label != null && label in 0..2
}

/** A candidate past record and its e5 cosine similarity to the target record. */
data class RelatedCandidate(val recordId: String, val similarity: Float)

/**
 * The final rule: label 2 only (failed / invalid = not shown), similarity DESC, ties by record id,
 * at most [limit]. Fewer than [limit] — or none — is a normal answer. Returns record ids in display order.
 */
fun selectWorthShowing(judged: List<Pair<RelatedCandidate, Int?>>, limit: Int = RelatedPolicy.MAX_RESULTS): List<String> =
    judged.asSequence()
        .filter { (_, label) -> label == RelatedPolicy.LABEL_WORTH_SHOWING }
        .map { it.first }
        .sortedWith(compareByDescending<RelatedCandidate> { it.similarity }.thenBy { it.recordId })
        .take(limit)
        .map { it.recordId }
        .toList()

/**
 * e5 candidate step: cosine to the target, similarity DESC, ties by record id, at most [limit].
 * The caller passes only records written before the target (never the target itself or later records).
 */
fun rankCandidates(
    target: FloatArray,
    candidates: List<Pair<String, FloatArray>>,
    limit: Int = RelatedPolicy.CANDIDATE_LIMIT,
): List<RelatedCandidate> =
    candidates.map { (id, vector) -> RelatedCandidate(id, cosine(target, vector)) }
        .sortedWith(compareByDescending<RelatedCandidate> { it.similarity }.thenBy { it.recordId })
        .take(limit)

/** Cosine similarity; 0 for a zero vector. */
fun cosine(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "embedding size ${a.size} != ${b.size}" }
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    return if (na == 0.0 || nb == 0.0) 0f else (dot / (sqrt(na) * sqrt(nb))).toFloat()
}
