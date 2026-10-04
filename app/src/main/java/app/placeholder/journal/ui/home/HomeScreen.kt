package app.placeholder.journal.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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
 * Home (initial Figma hierarchy): small date → hero question → a writing space that opens the editor →
 * 다시 만난 생각 → 최근 기록. No brand header — the app name lives on the splash; Home leads with
 * "leave today's thought". Sections below appear only when they have something real to show.
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = t.spacing.screenPadding, end = t.spacing.screenPadding, top = t.spacing.xl, bottom = t.spacing.xxl),
        ) {
            item(key = "write") { WriteToday(today = TimeFormat.monthDayWeekday(state.today), onClick = onWrite) }

            if (!state.loading) {
                state.resurfaced?.let { resurfaced ->
                    item(key = "resurfaced-title") {
                        SectionTitle(
                            "다시 만난 생각",
                            Modifier.padding(top = sectionGap + t.spacing.xs, bottom = titleGap),
                            subtitle = "시간이 지나 다시 나타난 지난 기록이에요.",
                        )
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
                            // compact on Home only: 16 → 10 vertical padding, 12 → 6 meta/body gap
                            contentPadding = PaddingValues(horizontal = t.spacing.md, vertical = 10.dp),
                            contentGap = 6.dp,
                            bodyMaxLines = 2,
                        )
                    }
                }
            }
            item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/**
 * Top of Home: a small date, the hero question (the first thing read), and a writing space.
 * The space is a plain surface, not a TextField: tapping anywhere opens the existing editor.
 * No mood button, no send arrow — nothing that pretends to be an input control.
 */
@Composable
private fun WriteToday(today: String, onClick: () -> Unit) {
    val t = AppTheme.tokens
    val interaction = remember { MutableInteractionSource() }
    Column {
        Text(
            text = today, // "10월 4일 일요일"
            style = MaterialTheme.typography.labelLarge, // 13sp Regular
            color = t.textSecondary,
        )
        Spacer(Modifier.height(t.spacing.xs))
        Text(
            text = "오늘은 어떤 생각이\n문득 떠올랐나요?",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 25.sp, lineHeight = 34.sp), // Bold
            color = t.textPrimary,
        )
        Spacer(Modifier.height(t.spacing.lg))
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = t.radii.hero,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(t.sizes.hairline, t.border),
            modifier = Modifier.fillMaxWidth().softGive(interaction),
        ) {
            Box(Modifier.heightIn(min = 112.dp).padding(t.spacing.lg)) {
                Text(
                    text = "지금 떠오르는 생각을 남겨보세요…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textSecondary,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "생각 남기기",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
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
        // Content-sized (no fixed height): 16 vertical padding, 10 between rows; horizontal 24 kept for the quote.
        Column(
            Modifier.padding(horizontal = t.spacing.xl, vertical = t.spacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ↶ 3개월 전쯤 — "a record that came back after time passed", in one muted ink.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(HistoryIcon, contentDescription = null, tint = t.textSecondary, modifier = Modifier.size(15.dp))
                Text(
                    text = resurfaced.period.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = t.textSecondary,
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

/** One style for every Home section title: 15 SemiBold in the secondary ink — quieter than the question above. */
@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    subtitle: String? = null,
) {
    val t = AppTheme.tokens
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = t.textPrimary,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
            }
        }
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

/**
 * Material "history" glyph (Apache 2.0), defined here because it is not in material-icons-core and the
 * extended icon set is not a dependency.
 */
private val HistoryIcon: ImageVector = ImageVector.Builder(
    name = "History",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(
        "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 " +
            "-3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 " +
            "9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z",
    ),
    fill = SolidColor(Color.Black),
).build()
