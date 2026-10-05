package app.placeholder.journal.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.placeholder.journal.ui.detail.RecordDetailScreen
import app.placeholder.journal.ui.editor.RecordEditorScreen
import app.placeholder.journal.ui.home.HomeScreen
import app.placeholder.journal.ui.records.RecordListScreen
import app.placeholder.journal.ui.related.RelatedMemoriesScreen

/**
 * Two top-level tabs (홈 · 기록) with a bottom bar; Editor and Detail are full screens above them.
 * Home → Editor(new) → save → back to Home
 * Home / Records → Detail → Editor(edit) → save → back to Detail → delete → back to the tab
 * Saving always ends the same way (back to where the editor was opened).
 * Save → "저장했어요" + jelly → (related results ready within the grace window) editor closes → Related Memories(target id)
 * → past record → Detail. Detail "이어지는 기록" → that record's Detail. Home is time-based only (no related entry).
 */
@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = when {
        destination?.hierarchy?.any { it.hasRoute(HomeRoute::class) } == true -> TopTab.Home
        destination?.hierarchy?.any { it.hasRoute(RecordListRoute::class) } == true -> TopTab.Records
        else -> null // Editor / Detail / Related Memories: no bottom bar
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0), // screens handle the status bar themselves
        bottomBar = { currentTab?.let { tab -> AppBottomBar(selected = tab, onSelect = { nav.openTab(it) }) } },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            // With the bar visible, its height (incl. the gesture area) is consumed, so screens' own
            // navigationBarsPadding() becomes 0. Without it (Editor / Detail) nothing changes.
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onWrite = { nav.navigate(RecordEditorRoute()) },
                    onOpenRecord = { id -> nav.navigate(RecordDetailRoute(id)) },
                    onSeeAllRecords = { nav.openTab(TopTab.Records) },
                )
            }
            composable<RecordListRoute> {
                RecordListScreen(
                    onNewRecord = { nav.navigate(RecordEditorRoute()) },
                    onOpenRecord = { id -> nav.navigate(RecordDetailRoute(id)) },
                )
            }
            composable<RecordEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<RecordEditorRoute>()
                RecordEditorScreen(
                    recordId = route.recordId,
                    onClose = { nav.popBackStack() },
                    // Runs after the save-success jelly: always back to where the user came from (Home, Records or
                    // Detail). Related records are analysed later in the background (M6), never on this path.
                    onSaved = { nav.popBackStack() },
                    // Related results were ready within the save grace window: the editor closes first, then Related
                    // Memories opens, so back / X returns to where the editor was opened.
                    onOpenRelated = { id ->
                        nav.popBackStack()
                        nav.navigate(RelatedMemoriesRoute(id))
                    },
                )
            }
            composable<RelatedMemoriesRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<RelatedMemoriesRoute>()
                RelatedMemoriesScreen(
                    recordId = route.recordId,
                    // Also called when the results disappear; popping by route never pops past this screen twice.
                    onClose = { nav.popBackStack<RelatedMemoriesRoute>(inclusive = true) },
                    onOpenRecord = { id -> nav.navigate(RecordDetailRoute(id)) },
                )
            }
            composable<RecordDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<RecordDetailRoute>()
                RecordDetailScreen(
                    recordId = route.recordId,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(RecordEditorRoute(route.recordId)) },
                    // Back to where the record was opened from (Home, Records or Related Memories).
                    onDeleted = { nav.popBackStack<RecordDetailRoute>(inclusive = true) },
                    onOpenRecord = { id -> nav.navigate(RecordDetailRoute(id)) },
                )
            }
        }
    }
}

/** Standard bottom-nav switch: one Home at the root, tab state saved and restored. */
private fun NavHostController.openTab(tab: TopTab) {
    val options: NavOptionsBuilder.() -> Unit = {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    when (tab) {
        TopTab.Home -> navigate(HomeRoute, options)
        TopTab.Records -> navigate(RecordListRoute, options)
    }
}
