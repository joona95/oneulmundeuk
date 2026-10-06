package app.oneulmundeuk.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oneulmundeuk.ui.components.CategoryChip
import app.oneulmundeuk.ui.components.EmptyState
import app.oneulmundeuk.ui.components.RecordCard
import app.oneulmundeuk.ui.container
import app.oneulmundeuk.ui.theme.AppTheme

/**
 * Figma `Semantic Search Results` (inside the 탐색 tab, bottom bar stays): back + search bar (filled) →
 * "N개의 기록을 찾았어요" · 관련도순 / 시간순 → RecordCards (max 3 lines) → tap = Record Detail.
 * States: searching (one quiet line) · results · nothing found · local AI unavailable ("준비하고 있어요", no count / sort).
 * Results come only from [app.oneulmundeuk.search.SemanticSearch] — no keyword fallback.
 */
@Composable
fun SearchResultsScreen(
    query: String,
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    categoryHint: String? = null,
    viewModel: SearchResultsViewModel = viewModel {
        val c = container()
        SearchResultsViewModel(c.semanticSearch, c.repository, query, categoryHint)
    },
) {
    val t = AppTheme.tokens
    val state by viewModel.state.collectAsStateWithLifecycle()
    val asked by viewModel.query.collectAsStateWithLifecycle()
    var text by rememberSaveable(asked) { mutableStateOf(asked) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.spacing.xxs),
            modifier = Modifier.fillMaxWidth().padding(start = t.spacing.xs, end = t.spacing.screenPadding, top = t.spacing.xs),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = t.textPrimary) }
            SemanticSearchBar(value = text, onValueChange = { text = it }, onSubmit = viewModel::submit, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                // Searching: one quiet line, no spinner / AI animation.
                SearchResultsState.Loading -> Text(
                    ExploreCopy.SEARCHING,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textTertiary,
                    modifier = Modifier.align(BiasAlignment(0f, -0.35f)),
                )
                SearchResultsState.Unavailable -> EmptyState(
                    title = ExploreCopy.UNAVAILABLE_TITLE,
                    body = ExploreCopy.UNAVAILABLE_BODY,
                    modifier = Modifier.align(BiasAlignment(0f, -0.35f)).padding(horizontal = t.spacing.screenPadding),
                )
                is SearchResultsState.Results -> if (s.items.isEmpty()) {
                    EmptyState(
                        title = ExploreCopy.NOTHING_FOUND_TITLE,
                        body = ExploreCopy.NOTHING_FOUND_BODY,
                        modifier = Modifier.align(BiasAlignment(0f, -0.35f)).padding(horizontal = t.spacing.screenPadding),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = t.spacing.screenPadding,
                            end = t.spacing.screenPadding,
                            top = t.spacing.lg,
                            bottom = t.spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(t.spacing.listGap),
                    ) {
                        item(key = "summary") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = t.spacing.lg - t.spacing.listGap),
                            ) {
                                Text(
                                    ExploreCopy.found(s.items.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = t.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.xxs)) {
                                    CategoryChip(ExploreCopy.SORT_RELEVANCE, s.sort == SearchSort.Relevance, onClick = { viewModel.setSort(SearchSort.Relevance) })
                                    CategoryChip(ExploreCopy.SORT_TIME, s.sort == SearchSort.Time, onClick = { viewModel.setSort(SearchSort.Time) })
                                }
                            }
                        }
                        items(s.items, key = { it.record.id }) { item ->
                            RecordCard(item, onClick = { onOpenRecord(item.record.id) })
                        }
                        item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
                    }
                }
            }
        }
    }
}
