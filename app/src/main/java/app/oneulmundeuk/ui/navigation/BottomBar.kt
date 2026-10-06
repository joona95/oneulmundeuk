package app.oneulmundeuk.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.oneulmundeuk.ui.theme.AppTheme

enum class TopTab(val label: String, val icon: ImageVector) {
    Home("홈", Icons.Filled.Home),
    Records("기록", Icons.AutoMirrored.Filled.List),
    Explore("탐색", Icons.Filled.Search),
    Settings("설정", Icons.Filled.Settings),
}

/**
 * Figma `Bottom Nav` (홈 · 기록 · 탐색 · 설정): white surface with a hairline top edge, 56 tall above the gesture
 * area (system navigation inset kept). Active = charcoal icon on a small neutral pill + charcoal label;
 * inactive = tertiary. Each tab is the full 56dp-tall cell, so the touch target stays ≥ 48dp.
 * Deliberately not the M3 NavigationBar (tonal container, 80 tall).
 */
@Composable
fun AppBottomBar(selected: TopTab, onSelect: (TopTab) -> Unit, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(thickness = t.sizes.hairline, color = t.border)
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = t.spacing.xs)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopTab.entries.forEach { tab ->
                    val on = tab == selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(t.spacing.hair, Alignment.CenterVertically),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = 44.dp, height = 24.dp)
                                .background(if (on) t.surfaceSecondary else Color.Transparent, t.radii.full),
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = null, // the label below names the tab
                                tint = if (on) t.textPrimary else t.textTertiary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) t.textPrimary else t.textTertiary,
                        )
                    }
                }
            }
        }
    }
}
