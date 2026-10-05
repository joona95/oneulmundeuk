package app.placeholder.journal

import android.app.Application
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.related.RecordChangeListener
import app.placeholder.journal.related.RelatedInvalidator
import app.placeholder.journal.related.RelatedRepository
import app.placeholder.journal.related.RelatedRuntime
import app.placeholder.journal.related.RelatedStore
import app.placeholder.journal.related.createRelatedRuntime
import app.placeholder.journal.resurface.DateBasedResurfacedRecordSelector
import app.placeholder.journal.resurface.ResurfacedRecordSelector

/** Manual DI: one small container instead of a DI framework. */
class AppContainer(app: Application) {
    val database: AppDatabase by lazy { AppDatabase.create(app) }
    /**
     * Per build type (src/debug, src/release). Release: no model yet → nothing queued / run / shown.
     * Debug: fake models behind a flag file (docs/m6-related-design.md "M6-4 debug fake").
     */
    val relatedRuntime: RelatedRuntime by lazy { createRelatedRuntime(app) { database } }
    /** Keeps related-record caches consistent after each committed write (M6-2). */
    val recordChangeListener: RecordChangeListener by lazy {
        RelatedInvalidator(
            database,
            analysisEnabled = { relatedRuntime.analysisEnabled },
            afterChange = { relatedRuntime.onRecordsChanged() },
        )
    }
    val repository: RecordRepository by lazy { RecordRepository(database, changeListener = recordChangeListener) }
    /** Stored related results for the UI; the active pipeline version stays inside [RelatedStore]. */
    val relatedRepository: RelatedRepository by lazy {
        RelatedRepository(RelatedStore(database.relatedDao(), relatedRuntime.activePipelineVersion), database.recordDao())
    }
    /** Home "다시 만난 생각" (date-based for now; swappable later). */
    val resurfacer: ResurfacedRecordSelector = DateBasedResurfacedRecordSelector()
}

class JournalApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.relatedRuntime.start()
    }
}
