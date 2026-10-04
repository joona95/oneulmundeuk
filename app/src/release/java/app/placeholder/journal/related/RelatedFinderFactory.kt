package app.placeholder.journal.related

import android.app.Application
import app.placeholder.journal.data.RecordRepository

/** Release: the production finder. Until M5 that is "nothing is related" (no Related Memories screen). */
fun createRelatedFinder(app: Application, repository: RecordRepository): RelatedRecordFinder = NoOpRelatedRecordFinder()
