package app.oneulmundeuk.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.data.settings.RelatedThoughtsStatus
import app.oneulmundeuk.ui.components.AppTopBar
import app.oneulmundeuk.ui.components.CategoryTag
import app.oneulmundeuk.ui.components.EmotionMarker
import app.oneulmundeuk.ui.components.MarkerShape
import app.oneulmundeuk.ui.container
import app.oneulmundeuk.ui.theme.AppTheme

/** Settings copy (user language only — no model / technology names). */
object SettingsCopy {
    const val TITLE = "설정"
    const val CATEGORY = "카테고리"
    const val CATEGORY_MANAGE = "카테고리 관리"
    const val CATEGORY_MANAGE_SUB = "추가 · 삭제"
    const val CATEGORY_NONE = "선택할 수 있는 카테고리가 없어요"
    const val SHAPE = "내 감정 조각"
    const val SHAPE_QUESTION = "어떤 모양으로 기록할까요?"
    const val SHAPE_HINT = "색은 감정마다 같아요. 기록할 때는 감정 이름이 함께 보여요."
    const val REMINDER = "다시 만나기 알림"
    const val REMINDER_ROW = "알림 받기"
    const val REMINDER_BODY = "예전의 생각을 가끔 다시 보여드려요."
    const val RELATED = "관련된 생각"
    const val RELATED_ROW = "관련된 생각 찾기"
    const val RELATED_BODY = "지금의 생각과 이어지는 예전 기록을 찾아드려요."
    const val RELATED_NOT_READY = "아직 이 기기에서 쓸 준비가 되지 않았어요. 준비 기능은 곧 추가돼요."
    const val RELATED_DOWNLOADING = "준비하고 있어요."
    const val RELATED_PRIVACY = "기록 분석은 기기 안에서 이루어져요."
}

/**
 * Settings (Figma `Settings`, v3): 카테고리 · 내 감정 조각 · 다시 만나기 알림 · 관련된 생각.
 * Group title above one card per group, hairline dividers inside — no nested cards. Values apply at once.
 */
@Composable
fun SettingsScreen(
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = viewModel {
        val c = container()
        SettingsViewModel(c.repository, c.settingsStore)
    },
) {
    val t = AppTheme.tokens
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title = SettingsCopy.TITLE)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = t.spacing.screenPadding, end = t.spacing.screenPadding, top = t.spacing.xs, bottom = t.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(t.spacing.xl),
        ) {
            item(key = "category") { CategorySection(state, onOpenCategories) }
            item(key = "shape") { ShapeSection(state.settings.markerShape, viewModel::setMarkerShape) }
            item(key = "reminder") {
                Group(SettingsCopy.REMINDER) {
                    Card {
                        SwitchRow(SettingsCopy.REMINDER_ROW, SettingsCopy.REMINDER_BODY, state.settings.reminderEnabled, viewModel::setReminderEnabled)
                    }
                }
            }
            item(key = "related") { RelatedSection(state, viewModel::setRelatedEnabled) }
            item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(state: SettingsUiState, onOpenCategories: () -> Unit) {
    val t = AppTheme.tokens
    Group(SettingsCopy.CATEGORY) {
        Card {
            if (state.loaded) {
                if (state.categories.isEmpty()) {
                    Text(SettingsCopy.CATEGORY_NONE, style = MaterialTheme.typography.bodySmall, color = t.textTertiary, modifier = Modifier.padding(t.spacing.md))
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(t.spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
                        modifier = Modifier.fillMaxWidth().padding(t.spacing.md),
                    ) {
                        state.categories.forEach { CategoryTag(it.name) }
                    }
                }
                Divider()
            }
            NavRow(SettingsCopy.CATEGORY_MANAGE, SettingsCopy.CATEGORY_MANAGE_SUB, onOpenCategories)
        }
    }
}

/** One shape for every emotion; each option previews it with real markers (3 emotions, so color reads as meaning). */
@Composable
private fun ShapeSection(selected: MarkerShape, onSelect: (MarkerShape) -> Unit) {
    val t = AppTheme.tokens
    Group(SettingsCopy.SHAPE, question = SettingsCopy.SHAPE_QUESTION) {
        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
            MarkerShape.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
                    row.forEach { shape -> ShapeOption(shape, shape == selected, { onSelect(shape) }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Text(SettingsCopy.SHAPE_HINT, style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
    }
}

private val PreviewEmotions = listOf(Emotion.CALM, Emotion.HAPPY, Emotion.TIRED)

@Composable
private fun ShapeOption(shape: MarkerShape, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Surface(
        onClick = onClick,
        shape = t.radii.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else t.sizes.hairline, if (selected) t.textPrimary else t.border),
        modifier = modifier.semantics { this.selected = selected },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
            modifier = Modifier.padding(horizontal = t.spacing.md, vertical = t.spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.xxs)) {
                PreviewEmotions.forEach { EmotionMarker(it, t.sizes.markerSm, shape = shape) }
            }
            Text(
                shape.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) t.textPrimary else t.textSecondary,
            )
        }
    }
}

@Composable
private fun RelatedSection(state: SettingsUiState, onToggle: (Boolean) -> Unit) {
    val t = AppTheme.tokens
    Group(SettingsCopy.RELATED) {
        Card {
            SwitchRow(SettingsCopy.RELATED_ROW, SettingsCopy.RELATED_BODY, state.settings.relatedEnabled, onToggle)
            val status = when (state.relatedStatus) {
                RelatedThoughtsStatus.MODEL_NOT_DOWNLOADED -> SettingsCopy.RELATED_NOT_READY
                RelatedThoughtsStatus.DOWNLOADING -> SettingsCopy.RELATED_DOWNLOADING
                RelatedThoughtsStatus.OFF, RelatedThoughtsStatus.READY -> null
            }
            if (status != null) {
                Divider()
                Text(status, style = MaterialTheme.typography.bodySmall, color = t.textSecondary, modifier = Modifier.padding(t.spacing.md))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.xxs)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = t.textTertiary, modifier = Modifier.size(14.dp))
            Text(SettingsCopy.RELATED_PRIVACY, style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
        }
    }
}

// ── shared pieces (Settings + 카테고리 관리) ──

/** Group title (quiet label) over its content. */
@Composable
internal fun Group(title: String, question: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = AppTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = t.textTertiary)
        if (question != null) Text(question, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
        content()
    }
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    val t = AppTheme.tokens
    Surface(
        shape = t.radii.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(t.sizes.hairline, t.border),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(content = content) }
}

@Composable
internal fun Divider() {
    val t = AppTheme.tokens
    Box(Modifier.fillMaxWidth().height(t.sizes.hairline).background(t.border))
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier) {
    val t = AppTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = t.textPrimary)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val t = AppTheme.tokens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = t.sizes.buttonMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = t.spacing.md, vertical = t.spacing.sm),
    ) {
        RowText(title, subtitle, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = t.textTertiary)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val t = AppTheme.tokens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = t.sizes.buttonMinHeight)
            .clickable(role = Role.Switch, onClick = { onCheckedChange(!checked) })
            .padding(horizontal = t.spacing.md, vertical = t.spacing.sm),
    ) {
        RowText(title, subtitle, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null) // the whole row toggles (one touch target, one announcement)
    }
}
