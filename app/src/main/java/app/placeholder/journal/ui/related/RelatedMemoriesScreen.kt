package app.placeholder.journal.ui.related

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.placeholder.journal.data.model.RecordWithCategory
import app.placeholder.journal.ui.components.AppTopBar
import app.placeholder.journal.ui.components.CategoryTag
import app.placeholder.journal.ui.components.EmotionMarker
import app.placeholder.journal.ui.components.RecordCard
import app.placeholder.journal.ui.components.rememberReduceMotion
import app.placeholder.journal.ui.components.softGive
import app.placeholder.journal.ui.container
import app.placeholder.journal.ui.theme.AppTheme
import app.placeholder.journal.ui.theme.TypeTokens
import app.placeholder.journal.util.TimeFormat

object RelatedMemoriesCopy {
    const val TITLE = "문득,\n예전의 생각이 떠올랐어요"
    const val NOW_LABEL = "방금 남긴 생각"
}

/** One quiet enter for the whole content (no stagger, no per-item motion). */
object RelatedMemoriesMotion {
    const val ENTER = 850 // fade + 12dp rise, slow arrival that settles
    const val ENTER_REDUCED = 150 // Reduce Motion: fade only
    const val RISE_DP = 12
    /** Decelerating (emphasized-decelerate): most of the rise happens early, then it eases into place. */
    val SETTLE_EASING = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/**
 * "문득, 그때" — Related Memories right after saving (Design Freeze: Thread B v3).
 * Close (X) → headline → 방금 남긴 생각 (the plain record card, no action) → past records as Memory Entries
 * in the finder's order. No subtitle, no count / rank / score, no AI explanation, no "비슷한".
 * M4: no bottom buttons (홈으로 / 이어서 생각 남기기 wait for the relation model); X or back closes.
 */
@Composable
fun RelatedMemoriesScreen(
    recordId: String,
    relatedIds: List<String>,
    onClose: () -> Unit,
    onOpenRecord: (String) -> Unit,
    viewModel: RelatedMemoriesViewModel = viewModel {
        RelatedMemoriesViewModel(container().repository, recordId, relatedIds)
    },
) {
    val t = AppTheme.tokens
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reduce = rememberReduceMotion()
    // Plays once on entry; coming back from a past record's Detail shows the content as it was.
    var entered by rememberSaveable { mutableStateOf(false) }
    val enter = remember { Animatable(if (entered) 1f else 0f) }
    LaunchedEffect(state.loading) {
        if (state.loading || entered) return@LaunchedEffect
        enter.animateTo(1f, tween(if (reduce) RelatedMemoriesMotion.ENTER_REDUCED else RelatedMemoriesMotion.ENTER, easing = RelatedMemoriesMotion.SETTLE_EASING))
        entered = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0), // the top bar handles the status bar; content handles the nav bar
        topBar = {
            AppTopBar(
                title = "",
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
                },
            )
        },
    ) { inner ->
        if (state.loading) return@Scaffold // keep the calm background for the first frame
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding())
                .graphicsLayer {
                    alpha = enter.value
                    translationY = if (reduce) 0f else (1f - enter.value) * RelatedMemoriesMotion.RISE_DP.dp.toPx()
                },
            contentPadding = PaddingValues(
                start = t.spacing.screenPadding,
                end = t.spacing.screenPadding,
                top = t.spacing.xs,
                bottom = t.spacing.xxl,
            ),
        ) {
            item(key = "headline") {
                Text(
                    text = RelatedMemoriesCopy.TITLE,
                    // display token (Bold), one step up from the old title: the screen's hero, like Home's question
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 38.sp),
                    color = t.textPrimary,
                )
            }
            state.current?.let { current ->
                item(key = "now-${current.record.id}") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
                        // headline + 방금 남긴 생각 read as one group (xl = 24dp); the wider gap is below it
                        modifier = Modifier.padding(top = t.spacing.xl),
                    ) {
                        Text(
                            text = RelatedMemoriesCopy.NOW_LABEL,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = t.textTertiary,
                        )
                        RecordCard(
                            item = current,
                            onClick = null, // M4: the card just shows what was saved
                            metaText = "지금 · ${TimeFormat.time(current.record.createdAt)}",
                        )
                    }
                }
            }
            itemsIndexed(state.past, key = { _, r -> "past-" + r.record.id }) { index, item ->
                MemoryEntry(
                    item = item,
                    onClick = { onOpenRecord(item.record.id) },
                    // present → past boundary: section gap (40dp) + the entry's own hairline; then the entries' rhythm
                    modifier = Modifier.padding(top = if (index == 0) t.spacing.sectionGap else t.spacing.xl),
                )
            }
            item(key = "nav-bar-space") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/**
 * Figma `Memory Entry` (Thread B v3): hairline → marker · "6개월 전" · date · category pill, all flowing from
 * the left → the user's own sentence in the memory type. No card container; the whole entry opens the
 * record's Detail. On narrow screens the date ellipsizes first; the pill is capped (categoryTagMax) too.
 */
@Composable
private fun MemoryEntry(item: RecordWithCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    val record = item.record
    val interaction = remember { MutableInteractionSource() }
    val memoryStyle = TypeTokens.forLineSeed(TypeTokens.memory).copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily)
    Column(
        verticalArrangement = Arrangement.spacedBy(t.spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .softGive(interaction),
    ) {
        Box(Modifier.fillMaxWidth().height(t.sizes.hairline).background(t.border))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.spacing.xs),
            modifier = Modifier.padding(top = t.spacing.sm),
        ) {
            record.emotion?.let { EmotionMarker(it, t.sizes.markerXs) }
            Text(TimeFormat.ago(record.createdAt), style = MaterialTheme.typography.labelMedium, color = t.textSecondary, maxLines = 1)
            Text("·", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
            Text(
                TimeFormat.longDate(record.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = t.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false), // takes only its width, so the pill follows it
            )
            item.categoryName?.let { CategoryTag(it) }
        }
        Text(
            text = record.text,
            style = memoryStyle,
            color = t.textPrimary,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
