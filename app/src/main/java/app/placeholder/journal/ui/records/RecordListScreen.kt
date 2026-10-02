package app.placeholder.journal.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.ui.components.EmptyState
import app.placeholder.journal.ui.components.RecordCard
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    onNewRecord: () -> Unit,
    onOpenRecord: (String) -> Unit,
    viewModel: RecordListViewModel = viewModel { RecordListViewModel(container().repository) },
) {
    val t = AppTheme.tokens
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("기록", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewRecord,
                shape = t.radii.lg,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "새 기록") }
        },
    ) { inner ->
        val list = groups
        when {
            list == null -> Unit // loading: keep the calm empty background
            list.isEmpty() -> EmptyState(
                title = "아직 남긴 생각이 없어요",
                body = "떠오르는 생각을 한 줄만 남겨도 괜찮아요.",
                modifier = Modifier.padding(inner).padding(top = t.spacing.xxxl),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(
                    start = t.spacing.screenPadding,
                    end = t.spacing.screenPadding,
                    top = t.spacing.xs,
                    bottom = t.sizes.fab + t.spacing.xxl, // FAB never covers the last card
                ),
                verticalArrangement = Arrangement.spacedBy(t.spacing.listGap),
            ) {
                list.forEachIndexed { index, group ->
                    item(key = "h-${group.day}") {
                        Text(
                            text = group.header,
                            style = MaterialTheme.typography.labelLarge,
                            color = t.textSecondary,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else t.spacing.md),
                        )
                    }
                    items(group.items, key = { it.record.id }) { item ->
                        RecordCard(item, onClick = { onOpenRecord(item.record.id) })
                    }
                }
            }
        }
    }
}
