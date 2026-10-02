package app.placeholder.journal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.placeholder.journal.ui.detail.RecordDetailScreen
import app.placeholder.journal.ui.editor.RecordEditorScreen
import app.placeholder.journal.ui.records.RecordListScreen

/**
 * Single stack for this milestone (the bottom tabs arrive with Home / Explore / Settings).
 * List → Editor(new) → save → back to List
 * List → Detail → Editor(edit) → save → back to Detail → delete → back to List
 */
@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = RecordListRoute) {
        composable<RecordListRoute> {
            RecordListScreen(
                onNewRecord = { nav.navigate(RecordEditorRoute()) },
                onOpenRecord = { id -> nav.navigate(RecordDetailRoute(id)) },
            )
        }
        composable<RecordEditorRoute> { entry ->
            val route = entry.toRoute<RecordEditorRoute>()
            RecordEditorScreen(
                recordId = route.recordId,
                onClose = { nav.popBackStack() },
                onSaved = {
                    // Future (Related Memories milestone): for a NEW record, ask RelatedRecordFinder and,
                    // if it returns results, navigate to the "문득, 예전의 생각이 떠올랐어요" screen instead.
                    nav.popBackStack()
                },
            )
        }
        composable<RecordDetailRoute> { entry ->
            val route = entry.toRoute<RecordDetailRoute>()
            RecordDetailScreen(
                recordId = route.recordId,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(RecordEditorRoute(route.recordId)) },
                onDeleted = { nav.popBackStack(RecordListRoute, inclusive = false) },
            )
        }
    }
}
