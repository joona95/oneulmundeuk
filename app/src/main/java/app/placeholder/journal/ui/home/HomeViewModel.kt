package app.placeholder.journal.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.resurface.ResurfacedRecord
import app.placeholder.journal.resurface.ResurfacedRecordSelector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate,
    /** "다시 만난 생각" — null hides the section (never an empty message). */
    val resurfaced: ResurfacedRecord? = null,
    /** Up to [HomeViewModel.RECENT_COUNT] newest records (the resurfaced one is not repeated here). */
    val recent: List<RecordWithCategory> = emptyList(),
)

/** Home is derived from the same Room Flow as Records, so new / edited / deleted records show up on both. */
class HomeViewModel(
    repository: RecordRepository,
    private val selector: ResurfacedRecordSelector,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    val state: StateFlow<HomeUiState> = repository.observeRecords()
        .map { records -> build(records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today = today()))

    private fun build(records: List<RecordWithCategory>): HomeUiState {
        val day = today()
        val resurfaced = selector.select(records, day, zone)
        return HomeUiState(
            loading = false,
            today = day,
            resurfaced = resurfaced,
            // DAO order is newest first.
            recent = records.filter { it.record.id != resurfaced?.item?.record?.id }.take(RECENT_COUNT),
        )
    }

    companion object {
        const val RECENT_COUNT = 3
    }
}
