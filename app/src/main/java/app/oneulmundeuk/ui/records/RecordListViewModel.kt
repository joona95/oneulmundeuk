package app.oneulmundeuk.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oneulmundeuk.data.CategoryPolicy
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.util.TimeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class DayGroup(val day: LocalDate, val header: String, val items: List<RecordWithCategory>)

enum class RecordsViewMode { List, Calendar }

data class CalendarState(
    val month: YearMonth,
    val today: LocalDate,
    val selectedDate: LocalDate,
    /** Sunday-first grid for [month]; null = blank cell. */
    val cells: List<LocalDate?>,
    /** Up to 3 dots per day (filtered). null = record without an emotion. */
    val dots: Map<LocalDate, List<Emotion?>>,
    /** Records of [selectedDate] (filtered), oldest → newest. */
    val selectedDayRecords: List<RecordWithCategory>,
)

data class RecordsUiState(
    val loading: Boolean,
    val mode: RecordsViewMode,
    val categories: List<CategoryEntity>,
    /** null = 전체 */
    val selectedCategoryId: String?,
    /** Any record at all (ignoring the filter) — decides the first-run empty state. */
    val hasAnyRecord: Boolean,
    /** List view (filtered), newest day first. */
    val groups: List<DayGroup>,
    val calendar: CalendarState,
)

/**
 * Records screen state: list / calendar view, month, selected date and category filter. Everything is
 * derived from the Room Flows, so edits and deletes made in Detail show up here automatically.
 * View mode and filter live only in this ViewModel (no persistence in this milestone).
 */
class RecordListViewModel(
    repository: RecordRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    private data class Selection(
        val mode: RecordsViewMode,
        val categoryId: String?,
        val month: YearMonth,
        val selectedDate: LocalDate,
    )

    private val selection = MutableStateFlow(
        today().let { Selection(RecordsViewMode.List, null, YearMonth.from(it), it) },
    )

    val state: StateFlow<RecordsUiState> = combine(
        repository.observeRecords(),
        repository.observeCategories(),
        selection,
    ) { records, categories, sel ->
        // Looking back: active categories + archived ones some record still uses ("삭제"한 '개발'도 기록이 있으면 필터 가능).
        val filterOptions = CategoryPolicy.recordsFilterOptions(categories, records)
        // A filter pointing at a category that is no longer offered falls back to 전체.
        val categoryId = sel.categoryId?.takeIf { id -> filterOptions.any { it.id == id } }
        val filtered = RecordsCalendar.filterByCategory(records, categoryId)
        val byDay = RecordsCalendar.groupByDay(filtered, zone)
        RecordsUiState(
            loading = false,
            mode = sel.mode,
            categories = filterOptions,
            selectedCategoryId = categoryId,
            hasAnyRecord = records.isNotEmpty(),
            // List: newest day first, records newest first within a day (DAO order) — unchanged from M1.
            groups = filtered.groupBy { TimeFormat.dayKey(it.record.createdAt, zone) }
                .map { (day, items) -> DayGroup(day, TimeFormat.dayHeader(items.first().record.createdAt, zone = zone), items) },
            calendar = CalendarState(
                month = sel.month,
                today = today(),
                selectedDate = sel.selectedDate,
                cells = RecordsCalendar.monthCells(sel.month),
                dots = byDay.mapValues { (_, items) -> RecordsCalendar.dots(items) },
                selectedDayRecords = byDay[sel.selectedDate].orEmpty(),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    private fun initialState(): RecordsUiState {
        val s = selection.value
        return RecordsUiState(
            loading = true,
            mode = s.mode,
            categories = emptyList(),
            selectedCategoryId = null,
            hasAnyRecord = false,
            groups = emptyList(),
            calendar = CalendarState(s.month, today(), s.selectedDate, RecordsCalendar.monthCells(s.month), emptyMap(), emptyList()),
        )
    }

    fun setMode(mode: RecordsViewMode) = selection.update { it.copy(mode = mode) }

    /** null = 전체 */
    fun selectCategory(categoryId: String?) = selection.update { it.copy(categoryId = categoryId) }

    fun previousMonth() = selection.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = selection.update { it.copy(month = it.month.plusMonths(1)) }

    fun selectDate(date: LocalDate) = selection.update { it.copy(selectedDate = date, month = YearMonth.from(date)) }
}
