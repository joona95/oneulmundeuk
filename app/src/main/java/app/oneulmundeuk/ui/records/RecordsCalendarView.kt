package app.oneulmundeuk.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.ui.theme.AppTheme
import app.oneulmundeuk.ui.theme.colors
import app.oneulmundeuk.util.TimeFormat
import java.time.LocalDate
import java.time.YearMonth

private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")
private val DayCircle = 34.dp
private val Dot = 4.dp

/**
 * 목록 / 캘린더 switch. A quiet pill track (surfaceSecondary) with the active half on white — the same
 * look as the Figma `Segmented` component. No Sage here: selection states are neutral.
 */
@Composable
fun ViewModeToggle(mode: RecordsViewMode, onChange: (RecordsViewMode) -> Unit, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(t.surfaceSecondary, t.radii.full)
            .padding(t.spacing.xxs)
            .selectableGroup(),
    ) {
        listOf(RecordsViewMode.List to "목록", RecordsViewMode.Calendar to "캘린더").forEach { (m, label) ->
            val selected = m == mode
            Surface(
                shape = t.radii.full,
                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                border = if (selected) BorderStroke(t.sizes.hairline, t.border) else null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(m) }),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) t.textPrimary else t.textSecondary,
                    )
                }
            }
        }
    }
}

/** ‹ 2026년 10월 › */
@Composable
fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = t.textSecondary)
        }
        Text(
            text = TimeFormat.monthTitle(month),
            style = MaterialTheme.typography.titleMedium,
            color = t.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = t.textSecondary)
        }
    }
}

/**
 * Month grid without cell boxes. Today = Sage number; selected = soft Sage circle (accent container).
 * Both at once = the soft circle with the Sage number, so they never fight. Dots are tiny and decorative.
 */
@Composable
fun MonthGrid(
    cells: List<LocalDate?>,
    today: LocalDate,
    selectedDate: LocalDate,
    dots: Map<LocalDate, List<Emotion?>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.spacing.hair)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { d ->
                Text(
                    text = d,
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = t.spacing.xxs),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                dots = dots[date].orEmpty(),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, isSelected: Boolean, dots: List<Emotion?>, onClick: () -> Unit) {
    val t = AppTheme.tokens
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = t.spacing.xxs)
            .semantics {
                contentDescription = TimeFormat.fullDate(date) +
                    (if (isToday) ", 오늘" else "") + (if (dots.isNotEmpty()) ", 기록 있음" else "")
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(DayCircle)
                .background(if (isSelected) scheme.primaryContainer else Color.Transparent, CircleShape),
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isToday -> scheme.primary
                    isSelected -> scheme.onPrimaryContainer
                    else -> t.textPrimary
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        // fixed height so rows with and without dots line up
        Row(Modifier.height(Dot), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.forEach { emotion ->
                Box(
                    Modifier
                        .size(Dot)
                        .background(emotion?.colors()?.fill ?: t.borderStrong, CircleShape),
                )
            }
        }
    }
}

/** "2026년 10월 3일 토요일" + that day's records, or one quiet line when there are none. */
@Composable
fun SelectedDayHeader(date: LocalDate, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Text(
        text = TimeFormat.fullDate(date),
        style = MaterialTheme.typography.labelLarge,
        color = t.textSecondary,
        modifier = modifier,
    )
}

@Composable
fun SelectedDayEmpty(modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Text(
        text = "이날은 아직 남긴 생각이 없어요.",
        style = MaterialTheme.typography.bodySmall,
        color = t.textTertiary,
        modifier = modifier.padding(vertical = t.spacing.xs),
    )
}
