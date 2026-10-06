package app.oneulmundeuk.ui.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oneulmundeuk.ui.theme.AppTheme

/**
 * Figma `Search Bar` (Explore and Semantic Search Results): 52 tall, radius 20.
 * empty = surfaceSecondary, no stroke, tertiary search icon + "무엇이든 물어보세요";
 * filled = white surface, 1.5dp strong border, charcoal icon, clear (X).
 * IME "search" submits; a blank question is ignored (no search runs).
 */
@Composable
fun SemanticSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.tokens
    val keyboard = LocalSoftwareKeyboardController.current
    val filled = value.isNotEmpty()
    Surface(
        shape = t.radii.hero,
        color = if (filled) MaterialTheme.colorScheme.surface else t.surfaceSecondary,
        border = if (filled) BorderStroke(1.5.dp, t.borderStrong) else null,
        modifier = modifier.fillMaxWidth().height(SEARCH_BAR_HEIGHT),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
            modifier = Modifier.padding(start = t.spacing.md, end = if (filled) t.spacing.xxs else t.spacing.md),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = if (filled) t.textPrimary else t.textTertiary,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = t.textPrimary),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (Explore.searchQuery(value) != null) {
                        keyboard?.hide()
                        onSubmit(value)
                    }
                }),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (!filled) {
                            Text(
                                ExploreCopy.SEARCH_PLACEHOLDER,
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        field()
                    }
                },
            )
            if (filled) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "지우기", tint = t.textTertiary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private val SEARCH_BAR_HEIGHT = 52.dp
