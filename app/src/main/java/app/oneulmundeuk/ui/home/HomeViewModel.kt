package app.oneulmundeuk.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.resurface.ResurfacedRecord
import app.oneulmundeuk.resurface.ResurfacedRecordSelector
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
    /**
     * No saved record at all (the current Room state — first run, or after every record was deleted).
     * Home then shows the shared no-records [app.oneulmundeuk.ui.components.EmptyState] under the writing space.
     */
    val noRecords: Boolean = false,
)

/** Home is derived from the same Room Flow as Records, so new / edited / deleted records show up on both. */
class HomeViewModel(
    repository: RecordRepository,
    private val selector: ResurfacedRecordSelector,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    val state: StateFlow<HomeUiState> = repository.observeRecords()
        .map { records -> state(records, selector, today(), zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today = today()))

    companion object {
        const val RECENT_COUNT = 3

        /** Pure: one Room emission → Home state. Only depends on the current records, never on history. */
        fun state(records: List<RecordWithCategory>, selector: ResurfacedRecordSelector, day: LocalDate, zone: ZoneId): HomeUiState {
            val resurfaced = selector.select(records, day, zone)
            return HomeUiState(
                loading = false,
                today = day,
                resurfaced = resurfaced,
                // DAO order is newest first.
                recent = records.filter { it.record.id != resurfaced?.item?.record?.id }.take(RECENT_COUNT),
                noRecords = records.isEmpty(),
            )
        }
    }
}
