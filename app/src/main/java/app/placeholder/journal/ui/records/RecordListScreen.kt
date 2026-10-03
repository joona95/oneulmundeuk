package app.placeholder.journal.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.ui.components.AppFab
import app.placeholder.journal.ui.components.AppTopBar
import app.placeholder.journal.ui.components.EmptyState
import app.placeholder.journal.ui.components.RecordCard
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppFonts
import app.placeholder.journal.ui.theme.AppTheme

/**
 * Records — List. "기록" here is the screen's function name, not the app brand.
 * Edge-to-edge: the list scrolls behind the transparent navigation bar; the FAB sits above it.
 */
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
        contentWindowInsets = WindowInsets(0), // the top bar handles the status bar; content handles the nav bar
        topBar = {
            AppTopBar(
                title = "기록",
                // TEMP(font-check): LINE Seed set directly on this one title, bypassing the theme. With
                // AppFonts.USE_LINE_SEED = false the rest of the UI is the system font and this title is not,
                // so both renderings can be compared on one screen. Remove after the font decision.
                titleStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = AppFonts.LineSeed),
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            val list = groups
            when {
                list == null -> Unit // loading: keep the calm empty background
                list.isEmpty() -> EmptyState(
                    title = "아직 남긴 생각이 없어요",
                    body = "떠오르는 생각을 한 줄만 남겨도 괜찮아요.",
                    // optical center: a little above the middle, clear of the FAB
                    modifier = Modifier.align(BiasAlignment(0f, -0.25f)).padding(horizontal = t.spacing.screenPadding),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = t.spacing.screenPadding,
                        end = t.spacing.screenPadding,
                        top = t.spacing.xs,
                        bottom = t.sizes.fab + t.spacing.xxl + t.spacing.md, // FAB never covers the last card
                    ),
                    verticalArrangement = Arrangement.spacedBy(t.spacing.listGap),
                ) {
                    list.forEachIndexed { index, group ->
                        item(key = "h-${group.day}") {
                            Text(
                                text = group.header,
                                style = MaterialTheme.typography.labelLarge,
                                color = t.textSecondary,
                                // groups are 20 apart (Figma): 12 list gap + 8
                                modifier = Modifier.padding(top = if (index == 0) 0.dp else t.spacing.xs),
                            )
                        }
                        items(group.items, key = { it.record.id }) { item ->
                            RecordCard(item, onClick = { onOpenRecord(item.record.id) })
                        }
                    }
                    item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
                }
            }

            // Figma: 20dp from the right edge, 16dp above the bottom chrome (here: the navigation bar).
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
