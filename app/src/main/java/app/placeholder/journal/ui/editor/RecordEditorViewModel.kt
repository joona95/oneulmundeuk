package app.placeholder.journal.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.db.CategoryEntity
import app.placeholder.journal.data.model.Emotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val canSave: Boolean get() = loaded && !saving && text.isNotBlank()
}

/** Create when [recordId] is null, edit otherwise. Emotion and category are optional. */
class RecordEditorViewModel(
    private val repository: RecordRepository,
    private val recordId: String?,
) : ViewModel() {
    val isEditing: Boolean = recordId != null

    private val _state = MutableStateFlow(EditorState(loaded = recordId == null))
    val state: StateFlow<EditorState> = _state.asStateFlow()

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
            if (recordId == null) repository.create(s.text, s.emotion, s.categoryId)
            else repository.update(recordId, s.text, s.emotion, s.categoryId)
            _state.update { it.copy(saving = false, saved = true) }
        }
    }
}
