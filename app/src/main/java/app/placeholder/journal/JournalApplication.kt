package app.placeholder.journal

import android.app.Application
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.related.NoOpRelatedRecordFinder
import app.placeholder.journal.related.RelatedRecordFinder
import app.placeholder.journal.resurface.DateBasedResurfacedRecordSelector
import app.placeholder.journal.resurface.ResurfacedRecordSelector

/** Manual DI: one small container instead of a DI framework. */
class AppContainer(app: Application) {
    val database: AppDatabase by lazy { AppDatabase.create(app) }
    val repository: RecordRepository by lazy { RecordRepository(database) }
    val relatedFinder: RelatedRecordFinder = NoOpRelatedRecordFinder()
    /** Home "다시 만난 생각" (date-based for now; swappable later). */
    val resurfacer: ResurfacedRecordSelector = DateBasedResurfacedRecordSelector()
}

class JournalApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
