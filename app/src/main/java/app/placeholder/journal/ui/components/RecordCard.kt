package app.placeholder.journal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.ui.theme.AppTheme
import app.placeholder.journal.util.TimeFormat

/**
 * List card: tiny color marker (no label) + time + category, then the user's text as the hero.
 * [contentPadding] / [contentGap] / [bodyMaxLines] default to the Records look; Home passes a more compact size.
 * [onClick] null → display only; [metaText] replaces the time line (e.g. "지금 · 오후 8:42").
 */
@Composable
fun RecordCard(
    item: RecordWithCategory,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppTheme.tokens.spacing.cardPadding),
    contentGap: Dp = AppTheme.tokens.spacing.sm,
    bodyMaxLines: Int = 3,
    metaText: String? = null,
) {
    val t = AppTheme.tokens
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(contentGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
                item.record.emotion?.let { EmotionMarker(it, t.sizes.markerXs) }
                Text(
                    text = metaText ?: TimeFormat.cardMeta(item.record.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                item.categoryName?.let { CategoryTag(it) }
            }
            Text(
                text = item.record.text,
                style = MaterialTheme.typography.bodyMedium,
                color = t.textPrimary,
                maxLines = bodyMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (onClick == null) {
        // Display only (e.g. "방금 남긴 생각" on Related Memories): same card, no press feedback.
        Surface(
            shape = t.radii.card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(t.sizes.hairline, t.border),
            modifier = modifier.fillMaxWidth(),
            content = content,
        )
    } else {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = t.radii.card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(t.sizes.hairline, t.border),
            modifier = modifier.fillMaxWidth().softGive(interaction),
            content = content,
        )
    }
}
