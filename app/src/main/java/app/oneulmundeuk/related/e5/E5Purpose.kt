package app.oneulmundeuk.related.e5

/**
 * The two ways the benchmarks used e5 — fixed, not tuned here. Each is its own embedding space (cache key), so a record
 * vector made for one is never reused for the other:
 *  - [RELATED] (M5, record ↔ record, frozen 157 pairs): "query: " on BOTH records.
 *  - [EXPLORE] (experiments/search S1, question ↔ record): question "query: ", record "passage: ".
 * One shared record embedding would mean changing one of these settings without a benchmark, so it is not done.
 */
enum class E5Purpose(val key: String, val recordPrefix: String, val queryPrefix: String) {
    RELATED("related-q-q", E5Embedder.QUERY, E5Embedder.QUERY),
    EXPLORE("explore-p-q", E5Embedder.PASSAGE, E5Embedder.QUERY),
}
