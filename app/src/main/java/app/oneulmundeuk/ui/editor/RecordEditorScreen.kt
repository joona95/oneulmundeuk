package app.oneulmundeuk.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oneulmundeuk.ui.components.AppTopBar
import app.oneulmundeuk.ui.components.CategoryChips
import app.oneulmundeuk.ui.components.EmotionPicker
import app.oneulmundeuk.ui.components.PrimaryButton
import app.oneulmundeuk.ui.components.SaveFeedbackKind
import app.oneulmundeuk.ui.components.SaveSuccessOverlay
import app.oneulmundeuk.ui.container
import app.oneulmundeuk.ui.theme.AppTheme
import app.oneulmundeuk.util.TimeFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordEditorScreen(
    recordId: String?,
    onClose: () -> Unit,
    /** After the save feedback: back to where the editor was opened (the same for every save). */
    onSaved: () -> Unit,
    /** Save feedback ended with related results ready in the grace window → Related Memories of that record (id only). */
    onOpenRelated: (String) -> Unit,
    viewModel: RecordEditorViewModel = viewModel {
        val c = container()
        RecordEditorViewModel(c.repository, c.relatedRepository, recordId)
    },
) {
    val t = AppTheme.tokens
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val openedAt = remember { System.currentTimeMillis() }
    // Keyboard open → the attribute panel goes compact (no section labels, no big button; "저장" stays in
    // the top bar) so the writing area keeps room to breathe.
    val keyboardOpen = WindowInsets.isImeVisible

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Saved → close the keyboard and show the save feedback; onSaved() (navigation) runs when it finishes.
    LaunchedEffect(state.saved) {
        if (state.saved) { keyboard?.hide(); focusManager.clearFocus() }
    }
    BackHandler(enabled = state.saved) { /* the feedback is ~0.7s; let it finish instead of double-popping */ }
    LaunchedEffect(Unit) { if (!viewModel.isEditing) focus.requestFocus() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0), // the panel owns the bottom insets (nav bar / keyboard)
            topBar = {
                AppTopBar(
                    title = if (viewModel.isEditing) "기록 수정" else "새 기록",
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
                    },
                    actions = {
                        TextButton(onClick = viewModel::save, enabled = state.canSave) {
                            Text("저장", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                )
            },
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = t.sizes.buttonMinHeight * 3)
                            .padding(bottom = t.spacing.xl)
                            .focusRequester(focus),
                        decorationBox = { field ->
                            Box {
                                if (state.text.isEmpty()) {
                                    Text("지금 떠오르는 생각을 남겨보세요…", style = MaterialTheme.typography.bodyLarge, color = t.textTertiary)
                                }
                                field()
                            }
                        },
                    )
                }

                // Attribute panel (Figma `attributes`): white surface, top radius 20, hairline top edge only,
                // and it runs under the navigation bar so there is no ivory strip below it.
                Surface(
                    shape = t.radii.sheetTop,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().topHairline(t.border, t.sizes.hairline, SheetRadius),
                ) {
                    Column(
                        Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
                            .padding(top = t.spacing.md, bottom = t.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(if (keyboardOpen) t.spacing.sm else t.spacing.md),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
                            AnimatedVisibility(visible = !keyboardOpen) { SheetLabel("지금 기분") }
                            EmotionPicker(
                                selected = state.emotion,
                                onSelect = viewModel::onEmotionChange,
                                modifier = Modifier.padding(horizontal = t.spacing.screenPadding),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
                            AnimatedVisibility(visible = !keyboardOpen) { SheetLabel("카테고리") }
                            CategoryChips(
                                categories = categories,
                                selectedId = state.categoryId,
                                onSelect = viewModel::onCategoryChange,
                                contentPadding = PaddingValues(horizontal = t.spacing.screenPadding),
                            )
                        }
                        AnimatedVisibility(visible = !keyboardOpen) {
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

        if (state.saved) {
            SaveSuccessOverlay(
                emotion = state.emotion,
                kind = if (state.firstRecord) SaveFeedbackKind.FirstRecord else SaveFeedbackKind.Saved,
                onFinished = onSaved,
                followUp = state.followUp,
                onFinishedToRelated = { state.savedRecordId?.let(onOpenRelated) ?: onSaved() },
            )
        }
    }
}

private val SheetRadius = 20 // dp — matches Radii.sheetTop

/** A 1dp line along the rounded top edge only (sides and bottom stay borderless, like Figma's topBorder). */
private fun Modifier.topHairline(color: Color, width: Dp, radiusDp: Int): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val r = radiusDp * density
    val half = w / 2
    val path = Path().apply {
        moveTo(half, half + r)
        arcTo(Rect(half, half, half + 2 * r, half + 2 * r), 180f, 90f, false)
        lineTo(size.width - r - half, half)
        arcTo(Rect(size.width - half - 2 * r, half, size.width - half, half + 2 * r), 270f, 90f, false)
    }
    drawPath(path, color, style = Stroke(width = w))
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
