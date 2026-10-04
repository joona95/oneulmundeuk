package app.placeholder.journal.ui.related

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.model.RecordWithCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RelatedMemoriesState(
    val loading: Boolean = true,
    /** The record just saved ("방금 남긴 생각"). */
    val current: RecordWithCategory? = null,
    /** Past records in the finder's order (most related first), at most 5. */
    val past: List<RecordWithCategory> = emptyList(),
)

/**
 * Shows the records the editor handed over (no second finder call). Observes the same Room Flow, so a past
 * record edited or deleted from its Detail is updated / dropped when coming back here.
 */
class RelatedMemoriesViewModel(
    repository: RecordRepository,
    private val recordId: String,
    private val relatedIds: List<String>,
) : ViewModel() {
    val state: StateFlow<RelatedMemoriesState> = repository.observeRecords()
        .map { all -> buildRelatedMemoriesState(all, recordId, relatedIds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelatedMemoriesState())
}

/** Pure: keeps [relatedIds] order, skips ids that no longer exist and the current record itself. */
fun buildRelatedMemoriesState(
    all: List<RecordWithCategory>,
    recordId: String,
    relatedIds: List<String>,
): RelatedMemoriesState {
    val byId = all.associateBy { it.record.id }
    return RelatedMemoriesState(
        loading = false,
        current = byId[recordId],
        past = relatedIds.distinct().filter { it != recordId }.mapNotNull { byId[it] },
    )
}
