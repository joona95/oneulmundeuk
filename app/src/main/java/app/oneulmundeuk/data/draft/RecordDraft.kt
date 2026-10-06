package app.oneulmundeuk.data.draft

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.oneulmundeuk.data.model.Emotion
import kotlinx.coroutines.flow.first

/**
 * The single in-progress *new* record (MVP: one draft). Lives outside Room on purpose: it is not a record, so Home,
 * Records, Explore, Related and resurfacing never see it. Edit mode never uses it.
 */
data class RecordDraft(val text: String, val emotion: Emotion?, val categoryId: String?) {
    companion object {
        /**
         * Worth keeping (and worth asking about on exit) = the same rule as saving: non-blank text.
         * Emotion / category alone cannot be saved as a record, so they alone are neither a draft nor a reason to ask.
         */
        fun of(text: String, emotion: Emotion?, categoryId: String?): RecordDraft? =
            if (text.isBlank()) null else RecordDraft(text, emotion, categoryId)
    }
}

/** Persistence boundary for the draft (fake in tests). All calls are suspend and run off the main thread. */
interface DraftStore {
    suspend fun load(): RecordDraft?
    suspend fun save(draft: RecordDraft)
    suspend fun clear()
}

/** Draft as three keys in the shared local Preferences DataStore. Emotion uses its stable Room key. */
class DataStoreDraftStore(private val prefs: DataStore<Preferences>) : DraftStore {
    override suspend fun load(): RecordDraft? {
        val p = prefs.data.first()
        val text = p[TEXT] ?: return null
        return RecordDraft.of(text, Emotion.fromKey(p[EMOTION]), p[CATEGORY])
    }

    override suspend fun save(draft: RecordDraft) {
        prefs.edit { p ->
            p[TEXT] = draft.text
            if (draft.emotion != null) p[EMOTION] = draft.emotion.key else p.remove(EMOTION)
            if (draft.categoryId != null) p[CATEGORY] = draft.categoryId else p.remove(CATEGORY)
        }
    }

    override suspend fun clear() {
        prefs.edit { p -> p.remove(TEXT); p.remove(EMOTION); p.remove(CATEGORY) }
    }

    private companion object {
        val TEXT = stringPreferencesKey("draft.new_record.text")
        val EMOTION = stringPreferencesKey("draft.new_record.emotion")
        val CATEGORY = stringPreferencesKey("draft.new_record.category_id")
    }
}
