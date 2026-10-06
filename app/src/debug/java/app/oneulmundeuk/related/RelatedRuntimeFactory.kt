package app.oneulmundeuk.related

import android.app.Application
import app.oneulmundeuk.data.db.AppDatabase

/** Debug: fake models behind a flag file (see [DebugRelatedRuntime]). Release has its own no-op factory. */
fun createRelatedRuntime(app: Application, database: () -> AppDatabase): RelatedRuntime = DebugRelatedRuntime(app, database)
