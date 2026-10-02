package app.placeholder.journal.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.util.TimeFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DayGroup(val day: LocalDate, val header: String, val items: List<RecordWithCategory>)

class RecordListViewModel(repository: RecordRepository) : ViewModel() {
    /** null while loading; empty list = no records yet. Newest day first (DAO orders by created_at DESC). */
    val groups: StateFlow<List<DayGroup>?> = repository.observeRecords()
        .map { records ->
            records.groupBy { TimeFormat.dayKey(it.record.createdAt) }
                .map { (day, items) -> DayGroup(day, TimeFormat.dayHeader(items.first().record.createdAt), items) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
