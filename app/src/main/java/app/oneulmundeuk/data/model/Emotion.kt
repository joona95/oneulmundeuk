package app.oneulmundeuk.data.model

/**
 * The 7 emotions. [key] is what Room stores — an explicit, stable string that must never change,
 * independent of the enum constant's name or ordinal (both may be renamed/reordered freely).
 * Display: [label] always accompanies the marker when choosing or reading in detail.
 */
enum class Emotion(val key: String, val label: String) {
    CALM("calm", "평온"),
    HAPPY("happy", "기쁨"),
    EXCITED("excited", "설렘"),
    SO_SO("so_so", "그냥 그래"),
    TIRED("tired", "지침"),
    ANXIOUS("anxious", "불안"),
    SAD("sad", "속상함");

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** Unknown keys (e.g. written by a newer app version) map to null instead of crashing. */
        fun fromKey(key: String?): Emotion? = key?.let { byKey[it] }
    }
}
