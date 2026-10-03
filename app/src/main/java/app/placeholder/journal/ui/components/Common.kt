package app.placeholder.journal.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.ui.theme.AppTheme

/** Sage primary action. Min height 48 so it grows with font scale instead of clipping. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = AppTheme.tokens
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = t.radii.full,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = t.surfaceSecondary,
            disabledContentColor = t.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = t.spacing.xl, vertical = t.spacing.sm),
        modifier = modifier.heightIn(min = t.sizes.buttonMinHeight),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * App bar matching the Figma `App Bar` component: 56 tall on the ivory background, no elevation.
 * - Title screens: no leading icon, title 22/600 aligned to the 20dp screen edge.
 * - Back / close screens: 40dp icon button at a 4dp inset, heading 17/600 title.
 * Draws behind the status bar (edge-to-edge) so the ivory runs to the top of the screen.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleStyle: TextStyle? = null,
) {
    val t = AppTheme.tokens
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .height(t.sizes.appBar)
                .padding(start = if (navigationIcon == null) t.spacing.screenPadding else t.spacing.xxs, end = t.spacing.xs),
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(Modifier.width(t.spacing.xxs))
            }
            Text(
                text = title,
                style = titleStyle
                    ?: if (navigationIcon == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = t.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
    }
}

/** Figma `FAB`: 56, radius 16, Sage, with a soft Sage-tinted shadow (not the default grey elevation). */
@Composable
fun AppFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = t.radii.lg,
        color = accent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        interactionSource = interaction,
        modifier = modifier
            .softGive(interaction)
            .shadow(8.dp, t.radii.lg, ambientColor = accent.copy(alpha = 0.25f), spotColor = accent.copy(alpha = 0.35f))
            .size(t.sizes.fab),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Column(
        modifier = modifier.fillMaxWidth().padding(t.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(t.spacing.sm),
    ) {
        EmotionMarker(Emotion.SO_SO, t.sizes.markerMd)
        Text(title, style = MaterialTheme.typography.titleMedium, color = t.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodySmall, color = t.textSecondary, textAlign = TextAlign.Center)
    }
}
