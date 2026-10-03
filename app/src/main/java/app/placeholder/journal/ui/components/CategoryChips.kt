package app.placeholder.journal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.placeholder.journal.data.db.CategoryEntity
import app.placeholder.journal.ui.theme.AppTheme

/** Horizontal-scrolling category chips. Selected = charcoal (Sage is reserved for primary actions). */
@Composable
fun CategoryChips(
    categories: List<CategoryEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val t = AppTheme.tokens
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            Surface(
                onClick = { onSelect(if (selected) null else category.id) },
                shape = t.radii.full,
                color = if (selected) t.textPrimary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(t.sizes.hairline, if (selected) t.textPrimary else t.border),
                modifier = Modifier
                    .heightIn(min = t.sizes.chipMinHeight)
                    .semantics { this.selected = selected; role = Role.Checkbox },
            ) {
                // Surface stretches its child to the 32dp min height; center the label inside it.
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.background else t.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = t.sizes.categoryTagMax * 1.5f)
                            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xxs + t.spacing.hair),
                    )
                }
            }
        }
    }
}

/** Small read-only category label inside cards. Long user-defined names are capped and ellipsized. */
@Composable
fun CategoryTag(name: String, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Surface(shape = t.radii.full, color = t.surfaceSecondary, modifier = modifier.widthIn(max = t.sizes.categoryTagMax)) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = t.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = t.spacing.xs, vertical = t.spacing.hair),
        )
    }
}
