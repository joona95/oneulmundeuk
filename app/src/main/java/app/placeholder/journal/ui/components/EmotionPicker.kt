package app.placeholder.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.ui.theme.AppTheme

/**
 * Emotion selection: always marker + label (users never have to guess from a shape).
 * Selected = a very subtle surface halo with a faint neutral edge, the marker one step larger, a semibold
 * label, and one squish → settle. No black ring. Tapping the selected emotion again clears it (optional field).
 */
@Composable
fun EmotionPicker(selected: Emotion?, onSelect: (Emotion?) -> Unit, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom, // the larger selected marker never shifts the labels
    ) {
        Emotion.entries.forEach { e ->
            val isSelected = e == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.selectable(
                    selected = isSelected,
                    onClick = { onSelect(if (isSelected) null else e) },
                    role = Role.RadioButton,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // feedback is the squish, not a ripple
                ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(t.sizes.markerLg + t.spacing.xs)
                        .background(if (isSelected) t.surfaceSecondary else Color.Transparent, CircleShape)
                        .border(t.sizes.hairline, if (isSelected) t.border else Color.Transparent, CircleShape),
                ) {
                    EmotionMarker(
                        emotion = e,
                        size = if (isSelected) t.sizes.markerLg else t.sizes.markerMd,
                        modifier = Modifier.squishOn(trigger = isSelected, active = isSelected),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = e.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) t.textPrimary else t.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
