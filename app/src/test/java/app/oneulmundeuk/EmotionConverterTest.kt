package app.oneulmundeuk

import app.oneulmundeuk.data.db.EmotionConverter
import app.oneulmundeuk.data.model.Emotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmotionConverterTest {
    private val converter = EmotionConverter()

    @Test
    fun `stored keys are the explicit stable keys`() {
        // Changing any of these would corrupt existing user data — this test must only ever grow.
        val expected = mapOf(
            Emotion.CALM to "calm",
            Emotion.HAPPY to "happy",
            Emotion.EXCITED to "excited",
            Emotion.SO_SO to "so_so",
            Emotion.TIRED to "tired",
            Emotion.ANXIOUS to "anxious",
            Emotion.SAD to "sad",
        )
        assertEquals(expected.keys, Emotion.entries.toSet())
        expected.forEach { (emotion, key) -> assertEquals(key, converter.toKey(emotion)) }
    }

    @Test
    fun `keys are not enum names or ordinals`() {
        Emotion.entries.forEach { e ->
            val key = converter.toKey(e)!!
            assert(key != e.name) { "key must not be the enum name: $key" }
            assert(key != e.ordinal.toString()) { "key must not be the ordinal: $key" }
        }
    }

    @Test
    fun `round trip`() {
        Emotion.entries.forEach { assertEquals(it, converter.fromKey(converter.toKey(it))) }
    }

    @Test
    fun `null and unknown keys map to null`() {
        assertNull(converter.toKey(null))
        assertNull(converter.fromKey(null))
        assertNull(converter.fromKey("CALM"))
        assertNull(converter.fromKey("0"))
        assertNull(converter.fromKey("future_emotion"))
    }
}
