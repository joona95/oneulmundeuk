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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.resurface.ResurfacedRecord
import app.placeholder.journal.ui.components.CategoryTag
import app.placeholder.journal.ui.components.EmotionMarker
import app.placeholder.journal.ui.components.RecordCard
import app.placeholder.journal.ui.components.softGive
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppTheme
import app.placeholder.journal.ui.theme.colors
import app.placeholder.journal.util.TimeFormat

/**
 * Home. Visual priority: 오늘문득 (page title) → 다시 만난 생각 (the core content, richest card) →
 * 오늘 기록하기 (a light, low action) → 최근 기록 (supporting). Sections appear only when they have
 * something real to show, so a new user sees just the title, the question and the action.
 * Spacing: tight inside a section, wider between sections.
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
    val sectionGap = t.spacing.xxl // between sections
    val titleGap = t.spacing.sm // section title → its content

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HomeTitle()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = t.spacing.screenPadding, end = t.spacing.screenPadding, top = t.spacing.xxs, bottom = t.spacing.xxl),
        ) {
            item(key = "write") { WriteToday(onClick = onWrite) }

            if (!state.loading) {
                state.resurfaced?.let { resurfaced ->
                    item(key = "resurfaced-title") {
                        SectionTitle("다시 만난 생각", Modifier.padding(top = sectionGap, bottom = titleGap))
                    }
                    item(key = "resurfaced-${resurfaced.item.record.id}") {
                        ResurfacedCard(resurfaced, onClick = { onOpenRecord(resurfaced.item.record.id) })
                    }
                }
                if (state.recent.isNotEmpty()) {
                    item(key = "recent-title") {
                        SectionTitle(
                            "최근 기록",
                            Modifier.padding(top = sectionGap, bottom = t.spacing.xxs),
                            action = "전체 보기",
                            onAction = onSeeAllRecords,
                        )
                    }
                    itemsIndexed(state.recent, key = { _, r -> "recent-" + r.record.id }) { index, item ->
                        RecordCard(
                            item,
                            onClick = { onOpenRecord(item.record.id) },
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else t.spacing.xs),
                            contentPadding = t.spacing.md,
                            bodyMaxLines = 2,
                        )
                    }
                }
            }
            item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/** Page title: one step calmer than the app-bar title so it never competes with the content. */
@Composable
private fun HomeTitle() {
    val t = AppTheme.tokens
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(t.sizes.appBar)
            .padding(horizontal = t.spacing.screenPadding),
    ) {
        Text(
            text = "오늘문득",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
            color = t.textPrimary,
        )
    }
}

/**
 * The write action: a quiet question and one low, wide row (56dp) that opens the existing editor.
 * Frequent but light — it must not read as the hero of Home.
 */
@Composable
private fun WriteToday(onClick: () -> Unit) {
    val t = AppTheme.tokens
    val interaction = remember { MutableInteractionSource() }
    Column(verticalArrangement = Arrangement.spacedBy(t.spacing.sm)) {
        Text(
            text = "오늘은 어떤 생각이 문득 떠올랐나요?",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary,
        )
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = t.radii.lg,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(t.sizes.hairline, t.border),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).softGive(interaction),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = t.spacing.lg, end = t.spacing.md),
            ) {
                Text(
                    text = "생각 남기기",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null, // the row's text names the action
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * "다시 만난 생각" card — the richest element on Home: the user's own words in the larger reading size,
 * a soft period label, then date / emotion / category. A faint (5%) wash and edge of the record's emotion.
 * No explanations, no "AI", no scores.
 */
@Composable
private fun ResurfacedCard(resurfaced: ResurfacedRecord, onClick: () -> Unit) {
    val t = AppTheme.tokens
    val record = resurfaced.item.record
    val surface = MaterialTheme.colorScheme.surface
    // The only tinted surface on Home: a ~5% wash of the record's own emotion color, and an edge drawn
    // from that same color instead of the neutral beige line the other cards use. No emotion → neutral.
    val emotionFill = record.emotion?.colors()?.fill
    val wash = emotionFill?.copy(alpha = 0.05f)?.compositeOver(surface) ?: surface
    val edge = emotionFill?.copy(alpha = 0.28f)?.compositeOver(surface) ?: t.border
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = t.radii.hero,
        color = wash,
        border = BorderStroke(t.sizes.hairline, edge),
        modifier = Modifier.fillMaxWidth().softGive(interaction),
    ) {
        Column(Modifier.padding(t.spacing.xl), verticalArrangement = Arrangement.spacedBy(t.spacing.md)) {
            // "3개월 전쯤" with a tiny Sage dot — a quiet sign that this thought came back on its own.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text(
                    text = resurfaced.period.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = record.text,
                style = MaterialTheme.typography.bodyLarge,
                color = t.textPrimary,
                maxLines = 5,
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

/** One style for every Home section title (15 Bold) — smaller than the page title, above body text. */
@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val t = AppTheme.tokens
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = t.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            // secondary action: small and quiet, but still a 48dp touch target
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = t.spacing.xs),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(action, style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
            }
        }
    }
}
