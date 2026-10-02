package app.placeholder.journal.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.ui.components.CategoryTag
import app.placeholder.journal.ui.components.EmotionMarker
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppTheme
import app.placeholder.journal.ui.theme.colors
import app.placeholder.journal.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: RecordDetailViewModel = viewModel { RecordDetailViewModel(container().repository, recordId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state) { if (state is DetailState.Gone) onDeleted() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "수정") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "더보기") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("삭제") }, onClick = { menuOpen = false; confirmDelete = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        (state as? DetailState.Loaded)?.let { DetailContent(it.item, Modifier.padding(inner)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 기록을 삭제할까요?") },
            text = { Text("삭제한 기록은 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun DetailContent(item: RecordWithCategory, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    val record = item.record
    val surface = MaterialTheme.colorScheme.surface
    // Hero: the record's emotion as a ~6% wash on a white surface — never a strong pastel card.
    val heroColor = record.emotion?.colors()?.fill?.copy(alpha = 0.06f)?.compositeOver(surface) ?: surface

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.spacing.screenPadding, vertical = t.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(t.spacing.xl),
    ) {
        Surface(shape = t.radii.hero, color = heroColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(t.spacing.lg), verticalArrangement = Arrangement.spacedBy(t.spacing.sm)) {
                Text(TimeFormat.fullDate(record.createdAt), style = MaterialTheme.typography.titleLarge, color = t.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
                    record.emotion?.let { emotion ->
                        // Detail = shape + color + label
                        Surface(shape = t.radii.full, color = surface) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(t.spacing.xxs),
                                modifier = Modifier.padding(start = t.spacing.xxs, end = t.spacing.sm, top = t.spacing.xxs, bottom = t.spacing.xxs),
                            ) {
                                EmotionMarker(emotion, t.sizes.markerSm)
                                Text(emotion.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = t.textSecondary)
                            }
                        }
                    }
                    item.categoryName?.let { CategoryTag(it) }
                    Text(TimeFormat.time(record.createdAt), style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
                }
            }
        }
        SelectionContainer {
            Text(record.text, style = MaterialTheme.typography.bodyLarge, color = t.textPrimary)
        }
        if (record.updatedAt > record.createdAt) {
            Text(
                "수정됨 · ${TimeFormat.cardMeta(record.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = t.textTertiary,
                modifier = Modifier.padding(bottom = t.spacing.xxl),
            )
        }
    }
}
