package app.oneulmundeuk.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.related.RelatedRecords
import app.oneulmundeuk.related.RelatedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailState {
    data object Loading : DetailState
    /** [related] = "이어지는 기록" (stored order, ≤ 5); empty hides the section. */
    data class Loaded(val item: RecordWithCategory, val related: List<RecordWithCategory> = emptyList()) : DetailState
    /** Deleted (or never existed) — the screen leaves. */
    data object Gone : DetailState
}

class RecordDetailViewModel(
    private val repository: RecordRepository,
    related: RelatedRepository,
    private val recordId: String,
) : ViewModel() {
    /** Observes the record, so edits made in the editor show up here immediately. */
    val state: StateFlow<DetailState> = combine(repository.observeRecord(recordId), related.observeRelated(recordId), ::detailState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    fun delete() {
        viewModelScope.launch { repository.delete(recordId) }
    }
}

/** Pure: no related results (null) → empty list → the "이어지는 기록" section, title included, is not drawn. */
fun detailState(item: RecordWithCategory?, related: RelatedRecords?): DetailState =
    if (item == null) DetailState.Gone else DetailState.Loaded(item, related?.related.orEmpty())
