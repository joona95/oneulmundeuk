package app.oneulmundeuk.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oneulmundeuk.data.CategoryPolicy
import app.oneulmundeuk.ui.components.AppTopBar
import app.oneulmundeuk.ui.container
import app.oneulmundeuk.ui.theme.AppTheme

/**
 * 카테고리 관리: add (trimmed, unique) and delete (= archive, after confirmation) for default and user categories alike.
 * No restore / reorder / rename in the MVP. Top back and system back both return to Settings (NavHost pop).
 */
@Composable
fun CategoryManageScreen(
    onBack: () -> Unit,
    viewModel: CategoryManageViewModel = viewModel { CategoryManageViewModel(container().repository) },
) {
    val t = AppTheme.tokens
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(
            title = SettingsCopy.CATEGORY_MANAGE,
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = t.textPrimary) }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = t.spacing.screenPadding, end = t.spacing.screenPadding, top = t.spacing.xs, bottom = t.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(t.spacing.xl),
        ) {
            item(key = "add") {
                Group("새 카테고리") {
                    Card {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = t.sizes.buttonMinHeight).padding(start = t.spacing.md, end = t.spacing.xxs),
                        ) {
                            Box(Modifier.weight(1f)) {
                                if (state.input.isEmpty()) {
                                    Text("카테고리 이름", style = MaterialTheme.typography.bodyMedium, color = t.textTertiary)
                                }
                                BasicTextField(
                                    value = state.input,
                                    onValueChange = viewModel::onInputChange,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = t.textPrimary),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { viewModel.add() }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            TextButton(onClick = viewModel::add, enabled = state.canAdd) { Text("추가", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
            item(key = "list") {
                Group(SettingsCopy.CATEGORY) {
                    Card {
                        if (state.categories.isEmpty()) {
                            Text(SettingsCopy.CATEGORY_NONE, style = MaterialTheme.typography.bodySmall, color = t.textTertiary, modifier = Modifier.padding(t.spacing.md))
                        }
                        state.categories.forEachIndexed { index, category ->
                            if (index > 0) Divider()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().heightIn(min = t.sizes.buttonMinHeight).padding(start = t.spacing.md, end = t.spacing.xxs),
                            ) {
                                Text(
                                    category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = t.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.requestDelete(category) }) {
                                    Text(CategoryPolicy.DELETE_CONFIRM, style = MaterialTheme.typography.labelLarge, color = t.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
        }
    }

    state.pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(CategoryPolicy.deleteTitle(category.name)) },
            text = { Text(CategoryPolicy.DELETE_BODY) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(CategoryPolicy.DELETE_CONFIRM, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text(CategoryPolicy.DELETE_CANCEL) } },
        )
    }
}
