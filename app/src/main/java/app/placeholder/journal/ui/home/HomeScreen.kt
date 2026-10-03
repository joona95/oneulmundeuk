package app.placeholder.journal.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.resurface.ResurfacedRecord
import app.placeholder.journal.ui.components.AppTopBar
import app.placeholder.journal.ui.components.CategoryTag
import app.placeholder.journal.ui.components.EmotionMarker
import app.placeholder.journal.ui.components.RecordCard
import app.placeholder.journal.ui.components.softGive
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppTheme
import app.placeholder.journal.ui.theme.colors
import app.placeholder.journal.util.TimeFormat

/**
 * Home: write today → meet a past thought again → recent records. Quiet on purpose: sections appear only
 * when they have something real to show (a new user sees just the "오늘 기록하기" card).
 */
@Composable
fun HomeScreen(
    onWrite: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onSeeAllRecords: () -> Unit,
    viewModel: HomeViewModel = viewModel {
        val c = container()
        HomeViewModel(c.repository, c.resurfacer)
    },
) {
    val t = AppTheme.tokens
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title = "오늘문득")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = t.spacing.screenPadding, end = t.spacing.screenPadding, top = t.spacing.xs, bottom = t.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(t.spacing.listGap),
        ) {
            item(key = "write") { WriteTodayCard(onClick = onWrite) }

            if (!state.loading) {
                state.resurfaced?.let { resurfaced ->
                    item(key = "resurfaced-title") { SectionTitle("다시 만난 생각", Modifier.padding(top = t.spacing.xxl - t.spacing.listGap)) }
                    item(key = "resurfaced-${resurfaced.item.record.id}") {
                        ResurfacedCard(resurfaced, onClick = { onOpenRecord(resurfaced.item.record.id) })
                    }
                }
                if (state.recent.isNotEmpty()) {
                    item(key = "recent-title") {
                        SectionTitle(
                            "최근 기록",
                            Modifier.padding(top = t.spacing.xxl - t.spacing.listGap),
                            action = "전체 보기",
                            onAction = onSeeAllRecords,
                        )
                    }
                    items(state.recent, key = { "recent-" + it.record.id }) { item ->
                        RecordCard(item, onClick = { onOpenRecord(item.record.id) })
                    }
                }
            }
            item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/** The main entry point: a quiet card that opens the existing editor. */
@Composable
private fun WriteTodayCard(onClick: () -> Unit) {
    val t = AppTheme.tokens
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = t.radii.hero,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(t.sizes.hairline, t.border),
        modifier = Modifier.fillMaxWidth().softGive(interaction),
    ) {
        Column(Modifier.padding(t.spacing.lg), verticalArrangement = Arrangement.spacedBy(t.spacing.lg)) {
            Text(
                text = "오늘은 어떤 생각이\n문득 떠올랐나요?",
                style = MaterialTheme.typography.titleLarge,
                color = t.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "지금 떠오르는 생각을 남겨보세요…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(t.sizes.iconButton).background(MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "오늘 기록하기",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * "다시 만난 생각" card: the user's own words first. Soft period label, then the original text,
 * then date / emotion / category. A faint (6%) wash of the record's emotion — the one place on Home
 * where color surfaces. No explanations, no "AI", no scores.
 */
@Composable
private fun ResurfacedCard(resurfaced: ResurfacedRecord, onClick: () -> Unit) {
    val t = AppTheme.tokens
    val record = resurfaced.item.record
    val surface = MaterialTheme.colorScheme.surface
    val wash = record.emotion?.colors()?.fill?.copy(alpha = 0.06f)?.compositeOver(surface) ?: surface
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = t.radii.hero,
        color = wash,
        border = BorderStroke(t.sizes.hairline, t.border),
        modifier = Modifier.fillMaxWidth().softGive(interaction),
    ) {
        Column(Modifier.padding(t.spacing.lg), verticalArrangement = Arrangement.spacedBy(t.spacing.sm)) {
            Text(
                text = resurfaced.period.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = t.textSecondary,
            )
            Text(
                text = record.text,
                style = MaterialTheme.typography.bodyLarge,
                color = t.textPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
                record.emotion?.let { EmotionMarker(it, t.sizes.markerXs) }
                Text(
                    text = TimeFormat.dotDate(record.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                resurfaced.item.categoryName?.let { CategoryTag(it) }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val t = AppTheme.tokens
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = t.textPrimary, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = onAction) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = t.textSecondary)
            }
        }
    }
}
