package app.oneulmundeuk.data.db

import androidx.room.TypeConverter
import app.oneulmundeuk.data.model.Emotion

/** Stores [Emotion] as its stable [Emotion.key] ("calm", "happy", "so_so", …) — never ordinal or enum name. */
class EmotionConverter {
    @TypeConverter
    fun toKey(emotion: Emotion?): String? = emotion?.key

    @TypeConverter
    fun fromKey(key: String?): Emotion? = Emotion.fromKey(key)
}
