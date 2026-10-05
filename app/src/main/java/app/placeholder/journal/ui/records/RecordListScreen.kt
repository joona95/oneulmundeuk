package app.placeholder.journal.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.ui.components.AppFab
import app.placeholder.journal.ui.components.AppTopBar
import app.placeholder.journal.ui.components.CategoryChips
import app.placeholder.journal.ui.components.EmptyState
import app.placeholder.journal.ui.components.RecordCard
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppTheme

/**
 * Records: 목록 / 캘린더 with one shared category filter. Page title 기록 (Home and Explore use their hero instead;
 * Settings uses 설정). Edge-to-edge: content scrolls behind the transparent navigation bar; the FAB sits above it.
 */
@Composable
fun RecordListScreen(
    onNewRecord: () -> Unit,
    onOpenRecord: (String) -> Unit,
    /** Set by Explore's 자주 등장한 주제: select this category (list view) once, then [onCategoryRequestHandled]. */
    requestedCategoryId: String? = null,
    onCategoryRequestHandled: () -> Unit = {},
    viewModel: RecordListViewModel = viewModel { RecordListViewModel(container().repository) },
) {
    LaunchedEffect(requestedCategoryId) {
        val id = requestedCategoryId ?: return@LaunchedEffect
        viewModel.setMode(RecordsViewMode.List)
        viewModel.selectCategory(id)
        onCategoryRequestHandled()
    }
    val t = AppTheme.tokens
    val state by viewModel.state.collectAsStateWithLifecycle()
    // One scroll position per view, hoisted above the mode switch so each survives switching back and
    // forth; rememberLazyListState is saveable, so it also survives going to Detail and back.
    val listScroll = rememberLazyListState()
    val calendarScroll = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0), // the top bar handles the status bar; content handles the nav bar
        topBar = { AppTopBar(title = "기록") },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            when {
                state.loading -> Unit // keep the calm empty background
                // First run, list view: the existing friendly empty state (nothing to switch or filter yet).
                !state.hasAnyRecord && state.mode == RecordsViewMode.List -> EmptyState(
                    title = "아직 남긴 생각이 없어요",
                    body = "떠오르는 생각을 한 줄만 남겨도 괜찮아요.",
                    modifier = Modifier.align(BiasAlignment(0f, -0.25f)).padding(horizontal = t.spacing.screenPadding),
                )
                // Fixed controls + scrolling content. The 목록/캘린더 switch and the filter used to be the
                // first item of the LazyColumn, so they scrolled off the top (and a list scroll offset carried
                // over into the calendar). Now they stay pinned while only the records / calendar scroll.
                else -> Column(Modifier.fillMaxSize()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(t.spacing.sm),
                        modifier = Modifier.padding(top = t.spacing.xs, bottom = t.spacing.listGap),
                    ) {
                        ViewModeToggle(
                            mode = state.mode,
                            onChange = viewModel::setMode,
                            modifier = Modifier.padding(horizontal = t.spacing.screenPadding),
                        )
                        if (state.categories.isNotEmpty()) {
                            CategoryChips(
                                categories = state.categories,
                                selectedId = state.selectedCategoryId,
                                onSelect = viewModel::selectCategory,
                                contentPadding = PaddingValues(horizontal = t.spacing.screenPadding),
                                allLabel = "전체",
                            )
                        }
                    }
                    // Each view keeps its own scroll position (see listScroll / calendarScroll above).
                    key(state.mode) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            state = if (state.mode == RecordsViewMode.List) listScroll else calendarScroll,
                            contentPadding = PaddingValues(
                                bottom = t.sizes.fab + t.spacing.xxl + t.spacing.md, // FAB never covers the last card
                            ),
                            verticalArrangement = Arrangement.spacedBy(t.spacing.listGap),
                        ) {
                            when (state.mode) {
                                RecordsViewMode.List -> listContent(state, onOpenRecord)
                                RecordsViewMode.Calendar -> calendarContent(state, viewModel, onOpenRecord)
                            }
                            item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
                        }
                    }
                }
            }

            // Figma: 20dp from the right edge, 16dp above the bottom chrome (here: the navigation bar).
            // Calendar view too: always "write a new record now" (no back-dating in this milestone).
            AppFab(
                onClick = onNewRecord,
                contentDescription = "새 기록",
                modifier = Modifier
                    .align(BiasAlignment(1f, 1f))
                    .navigationBarsPadding()
                    .padding(end = t.spacing.screenPadding, bottom = t.spacing.md),
            )
        }
    }
}

private fun LazyListScope.listContent(state: RecordsUiState, onOpenRecord: (String) -> Unit) {
    if (state.groups.isEmpty()) {
        item(key = "filtered-empty") { FilteredEmpty() }
        return
    }
    state.groups.forEachIndexed { index, group ->
        item(key = "h-${group.day}") {
            val t = AppTheme.tokens
            Text(
                text = group.header,
                style = MaterialTheme.typography.labelLarge,
                color = t.textSecondary,
                // groups are 20 apart (Figma): 12 list gap + 8; the first sits 8 below the filters
                modifier = Modifier.padding(horizontal = t.spacing.screenPadding).padding(top = t.spacing.xs),
            )
        }
        items(group.items, key = { it.record.id }) { item ->
            val t = AppTheme.tokens
            RecordCard(item, onClick = { onOpenRecord(item.record.id) }, modifier = Modifier.padding(horizontal = t.spacing.screenPadding))
        }
    }
}

private fun LazyListScope.calendarContent(
    state: RecordsUiState,
    viewModel: RecordListViewModel,
    onOpenRecord: (String) -> Unit,
) {
    val cal = state.calendar
    item(key = "calendar") {
        val t = AppTheme.tokens
        Column(Modifier.fillMaxWidth().padding(horizontal = t.spacing.md)) {
            MonthHeader(cal.month, onPrevious = viewModel::previousMonth, onNext = viewModel::nextMonth)
            MonthGrid(
                cells = cal.cells,
                today = cal.today,
                selectedDate = cal.selectedDate,
                dots = cal.dots,
                onSelect = viewModel::selectDate,
            )
        }
    }
    item(key = "day-header-${cal.selectedDate}") {
        val t = AppTheme.tokens
        SelectedDayHeader(cal.selectedDate, Modifier.padding(horizontal = t.spacing.screenPadding).padding(top = t.spacing.sm))
    }
    if (cal.selectedDayRecords.isEmpty()) {
        item(key = "day-empty") {
            val t = AppTheme.tokens
            SelectedDayEmpty(Modifier.padding(horizontal = t.spacing.screenPadding))
        }
    } else {
        items(cal.selectedDayRecords, key = { "day-" + it.record.id }) { item ->
            val t = AppTheme.tokens
            RecordCard(item, onClick = { onOpenRecord(item.record.id) }, modifier = Modifier.padding(horizontal = t.spacing.screenPadding))
        }
    }
}

/** A filter with no matches: one quiet line, not a full empty screen. */
@Composable
private fun FilteredEmpty() {
    val t = AppTheme.tokens
    Text(
        text = "이 카테고리에는 아직 남긴 생각이 없어요.",
        style = MaterialTheme.typography.bodySmall,
        color = t.textTertiary,
        modifier = Modifier.padding(horizontal = t.spacing.screenPadding, vertical = t.spacing.md + 4.dp),
    )
}
