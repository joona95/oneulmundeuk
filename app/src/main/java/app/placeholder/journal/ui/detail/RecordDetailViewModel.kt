package app.placeholder.journal.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.model.RecordWithCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val item: RecordWithCategory) : DetailState
    /** Deleted (or never existed) — the screen leaves. */
    data object Gone : DetailState
}

class RecordDetailViewModel(
    private val repository: RecordRepository,
    private val recordId: String,
) : ViewModel() {
    /** Observes the record, so edits made in the editor show up here immediately. */
    val state: StateFlow<DetailState> = repository.observeRecord(recordId)
        .map { if (it == null) DetailState.Gone else DetailState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    fun delete() {
        viewModelScope.launch { repository.delete(recordId) }
    }
}
