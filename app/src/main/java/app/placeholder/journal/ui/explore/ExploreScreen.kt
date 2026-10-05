package app.placeholder.journal.ui.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.data.db.CategoryEntity
import app.placeholder.journal.ui.components.CategoryChip
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppTheme

/**
 * Explore (Figma `Explore`, Design Freeze: "과거의 나에게 물어보세요."): the user asks their past self, by meaning.
 * hero (title · subtitle · search bar · on-device note) → 이렇게 물어볼 수 있어요 → 자주 등장한 주제.
 * No page title — the hero is the title. Sections 40 apart, 12 inside a section (Figma tokens).
 * Suggested questions (deterministic templates over the user's records — date periods / recurring categories) run the
 * search; topic chips open Records filtered by that category.
 */
@Composable
fun ExploreScreen(
    /** (question, categoryHint) — the hint is non-null only for an app category suggestion. */
    onSearch: (String, String?) -> Unit,
    onOpenTopic: (String) -> Unit,
    viewModel: ExploreViewModel = viewModel {
        val c = container()
        ExploreViewModel(c.repository, semanticAvailable = c.relatedRuntime.textEmbedder != null)
    },
) {
    val t = AppTheme.tokens
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = t.spacing.screenPadding,
                end = t.spacing.screenPadding,
                top = t.spacing.xs + t.spacing.xl,
                bottom = t.spacing.xxl,
            ),
        ) {
            item(key = "hero") {
                Column(verticalArrangement = Arrangement.spacedBy(t.spacing.sm)) {
                    Text(
                        ExploreCopy.HERO,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 25.sp, lineHeight = 34.sp), // Home's hero
                        color = t.textPrimary,
                    )
                    Text(ExploreCopy.SUBTITLE, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
                    SemanticSearchBar(value = query, onValueChange = { query = it }, onSubmit = { Explore.searchQuery(it)?.let { q -> onSearch(q, null) } })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.xxs)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = t.textTertiary, modifier = Modifier.size(14.dp))
                        Text(ExploreCopy.PRIVACY, style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
                    }
                }
            }
            if (questions.isNotEmpty()) { // nothing answerable yet (few records) → no section, never filler questions
                item(key = "examples") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(t.spacing.sm),
                        modifier = Modifier.padding(top = t.spacing.sectionGap),
                    ) {
                        Text(ExploreCopy.EXAMPLES_TITLE, style = MaterialTheme.typography.titleMedium, color = t.textPrimary)
                        ExampleQueries(questions, onPick = { onSearch(it.text, it.categoryId) })
                    }
                }
            }
            if (topics.isNotEmpty()) {
                item(key = "topics") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(t.spacing.sm),
                        modifier = Modifier.padding(top = t.spacing.sectionGap),
                    ) {
                        Text(ExploreCopy.TOPICS_TITLE, style = MaterialTheme.typography.titleMedium, color = t.textPrimary)
                        Topics(topics, onOpenTopic)
                    }
                }
            }
            item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/** One card, hairline between rows: search icon · the question · ›. A tap asks that question (search runs). */
@Composable
private fun ExampleQueries(questions: List<SuggestedQuestion>, onPick: (SuggestedQuestion) -> Unit) {
    val t = AppTheme.tokens
    Surface(
        shape = t.radii.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(t.sizes.hairline, t.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            questions.forEachIndexed { index, q ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(t.sizes.hairline).background(t.border))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = { onPick(q) })
                        .padding(horizontal = t.spacing.cardPadding, vertical = t.spacing.md),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = t.textSecondary, modifier = Modifier.size(16.dp))
                    Text(q.text, style = MaterialTheme.typography.bodyMedium, color = t.textPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = t.textTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Wrapping category chips (Figma: wrap, 8 apart both ways). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Topics(topics: List<CategoryEntity>, onOpen: (String) -> Unit) {
    val t = AppTheme.tokens
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(t.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
    ) {
        topics.forEach { c -> CategoryChip(c.name, selected = false, onClick = { onOpen(c.id) }) }
    }
}
