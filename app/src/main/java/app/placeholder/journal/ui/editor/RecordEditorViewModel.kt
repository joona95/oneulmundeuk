package app.placeholder.journal.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.db.CategoryEntity
import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.related.NoOpRelatedRecordFinder
import app.placeholder.journal.related.RelatedRecordFinder
import app.placeholder.journal.related.relatedIdsToShow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val text: String = "",
    val emotion: Emotion? = null,
    val categoryId: String? = null,
    /** Original creation time when editing; null for a new record. */
    val createdAt: Long? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** True when the record just created is the only one (shows the first-record save message). */
    val firstRecord: Boolean = false,
) {
    val canSave: Boolean get() = loaded && !saving && !saved && text.isNotBlank() // !saved: no double save during the feedback
}

/** A new record that has related past records: open Related Memories with these (finder order, ≤ 5). */
data class RelatedAfterSave(val recordId: String, val relatedIds: List<String>)

/**
 * Create when [recordId] is null, edit otherwise. Emotion and category are optional.
 * M4: a NEW record that is not the first one also asks [relatedFinder] — started right after the write,
 * so it runs while the save jelly plays. Edits and the first record never ask.
 */
class RecordEditorViewModel(
    private val repository: RecordRepository,
    private val recordId: String?,
    private val relatedFinder: RelatedRecordFinder = NoOpRelatedRecordFinder(),
) : ViewModel() {
    val isEditing: Boolean = recordId != null

    private val _state = MutableStateFlow(EditorState(loaded = recordId == null))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var createdId: String? = null
    private var related: Deferred<List<String>>? = null

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val r = repository.getRecord(recordId)
                _state.update {
                    if (r == null) it.copy(loaded = true)
                    else it.copy(text = r.text, emotion = r.emotion, categoryId = r.categoryId, createdAt = r.createdAt, loaded = true)
                }
            }
        }
    }

    fun onTextChange(text: String) = _state.update { it.copy(text = text) }
    fun onEmotionChange(emotion: Emotion?) = _state.update { it.copy(emotion = emotion) }
    fun onCategoryChange(categoryId: String?) = _state.update { it.copy(categoryId = categoryId) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            var first = false
            if (recordId == null) {
                val id = repository.create(s.text, s.emotion, s.categoryId)
                // Read after the write has committed; the save itself is never delayed by the feedback UI.
                first = repository.observeRecords().first().size == 1
                if (!first) {
                    createdId = id
                    related = viewModelScope.async { findRelated(id) }
                }
            } else {
                repository.update(recordId, s.text, s.emotion, s.categoryId)
            }
            _state.update { it.copy(saving = false, saved = true, firstRecord = first) }
        }
    }

    /**
     * Where to go after the save feedback: null → the normal flow (no results, an edit, or the first record);
     * otherwise Related Memories. Waits for the finder if it is still running (no spinner in M4).
     */
    suspend fun awaitRelated(): RelatedAfterSave? {
        val id = createdId ?: return null
        val ids = related?.await().orEmpty()
        return if (ids.isEmpty()) null else RelatedAfterSave(id, ids)
    }

    private suspend fun findRelated(id: String): List<String> = try {
        val saved = repository.observeRecord(id).first()
        if (saved == null) emptyList()
        else relatedIdsToShow(id, relatedFinder.findRelated(saved, RelatedRecordFinder.LIMIT))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList() // a failing finder must never break saving: fall back to the normal flow
    }
}
