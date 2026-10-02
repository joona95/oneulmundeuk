package app.placeholder.journal.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.ui.components.CategoryChips
import app.placeholder.journal.ui.components.EmotionPicker
import app.placeholder.journal.ui.components.PrimaryButton
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppTheme
import app.placeholder.journal.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditorScreen(
    recordId: String?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RecordEditorViewModel = viewModel { RecordEditorViewModel(container().repository, recordId) },
) {
    val t = AppTheme.tokens
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val openedAt = remember { System.currentTimeMillis() }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(Unit) { if (!viewModel.isEditing) focus.requestFocus() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
                },
                title = {
                    Text(if (viewModel.isEditing) "기록 수정" else "새 기록", style = MaterialTheme.typography.titleMedium)
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text("저장", style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding(),
        ) {
            // Writing area — long text stays comfortable (bodyLarge 17/30).
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = t.spacing.screenPadding, vertical = t.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(t.spacing.md),
            ) {
                val stamp = state.createdAt ?: openedAt
                Text(
                    text = "${TimeFormat.fullDate(stamp)} · ${TimeFormat.time(stamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textTertiary,
                )
                BasicTextField(
                    value = state.text,
                    onValueChange = viewModel::onTextChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.textPrimary),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = t.sizes.buttonMinHeight * 4).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (state.text.isEmpty()) {
                                Text("지금 떠오르는 생각을 남겨보세요…", style = MaterialTheme.typography.bodyLarge, color = t.textTertiary)
                            }
                            inner()
                        }
                    },
                )
            }

            // Attribute sheet: emotion (marker + label) and category, then the primary action.
            val borderColor = t.border
            Surface(
                shape = t.radii.sheetTop,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().drawBehind {
                    drawLine(borderColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
                },
            ) {
                Column(
                    Modifier.padding(top = t.spacing.md, bottom = t.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(t.spacing.md),
                ) {
                    SheetLabel("지금 기분")
                    EmotionPicker(
                        selected = state.emotion,
                        onSelect = viewModel::onEmotionChange,
                        modifier = Modifier.padding(horizontal = t.spacing.screenPadding),
                    )
                    SheetLabel("카테고리")
                    CategoryChips(
                        categories = categories,
                        selectedId = state.categoryId,
                        onSelect = viewModel::onCategoryChange,
                        contentPadding = PaddingValues(horizontal = t.spacing.screenPadding),
                    )
                    PrimaryButton(
                        text = "저장하기",
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = t.spacing.screenPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    val t = AppTheme.tokens
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = t.textTertiary,
        modifier = Modifier.padding(horizontal = t.spacing.screenPadding),
    )
}
