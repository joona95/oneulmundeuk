package app.placeholder.journal.related

import android.app.Application
import app.placeholder.journal.data.db.AppDatabase

/** Debug: fake models behind a flag file (see [DebugRelatedRuntime]). Release has its own no-op factory. */
fun createRelatedRuntime(app: Application, database: () -> AppDatabase): RelatedRuntime = DebugRelatedRuntime(app, database)
