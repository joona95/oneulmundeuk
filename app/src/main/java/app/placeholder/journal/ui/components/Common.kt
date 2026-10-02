package app.placeholder.journal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
