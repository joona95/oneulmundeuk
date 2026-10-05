package app.placeholder.journal.related

import android.app.Application
import app.placeholder.journal.data.db.AppDatabase

/** Release (M6-4): no model yet — nothing is queued, nothing runs, nothing is shown. Never creates fake results. */
@Suppress("UNUSED_PARAMETER")
fun createRelatedRuntime(app: Application, database: () -> AppDatabase): RelatedRuntime = NoRelatedRuntime
