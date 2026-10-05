package app.placeholder.journal.ui.related

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.related.RelatedRecords
import app.placeholder.journal.related.RelatedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RelatedMemoriesState(
    val loading: Boolean = true,
    /** The record the past records continue from ("이 생각에서"). */
    val current: RecordWithCategory? = null,
    /** Past records in the stored order (most related first), 1..5. */
    val past: List<RecordWithCategory> = emptyList(),
    /** No results to show (0 / stale / PENDING / RUNNING / target deleted) → the screen closes itself. */
    val gone: Boolean = false,
)

/**
 * Reads the stored results of [recordId] (the route carries only that id). Observes them, so a past record edited
 * or deleted from its Detail is updated / dropped when coming back, and the screen leaves once nothing is left.
 */
class RelatedMemoriesViewModel(
    related: RelatedRepository,
    recordId: String,
) : ViewModel() {
    val state: StateFlow<RelatedMemoriesState> = related.observeRelated(recordId)
        .map(::relatedMemoriesState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelatedMemoriesState())
}

fun relatedMemoriesState(related: RelatedRecords?): RelatedMemoriesState =
    if (related == null) RelatedMemoriesState(loading = false, gone = true)
    else RelatedMemoriesState(loading = false, current = related.target, past = related.related)
