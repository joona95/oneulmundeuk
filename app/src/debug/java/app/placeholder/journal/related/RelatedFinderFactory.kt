package app.placeholder.journal.related

import android.app.Application
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.model.RecordWithCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug builds only (src/debug — not compiled into release). Same production finder as release, unless the
 * M4 verification flag file exists; then [DebugRelatedRecordFinder] answers so the Related Memories flow can be
 * checked on a device. See [DebugRelatedRecordFinder.FLAG_FILE] for how to turn it on / off.
 */
fun createRelatedFinder(app: Application, repository: RecordRepository): RelatedRecordFinder =
    DebugRelatedRecordFinder(app, repository, fallback = NoOpRelatedRecordFinder())

/**
 * M4 UX check only — NOT a relatedness algorithm. When ON, returns up to [DEMO_COUNT] older records,
 * oldest first (deterministic: createdAt, then id). Reads records as they are; never writes the database.
 * OFF (default, flag file absent) → delegates to [fallback].
 *
 * ON:  adb shell run-as app.placeholder.journal sh -c 'mkdir -p files && touch files/debug_related_finder_on'
 * OFF: adb shell run-as app.placeholder.journal rm -f files/debug_related_finder_on
 * Checked on every save, so no restart is needed. Needs at least one older record (the first record never
 * runs the finder).
 */
class DebugRelatedRecordFinder(
    private val app: Application,
    private val repository: RecordRepository,
    private val fallback: RelatedRecordFinder,
) : RelatedRecordFinder {

    override suspend fun findRelated(record: RecordWithCategory, limit: Int): List<RecordWithCategory> {
        if (!isOn()) return fallback.findRelated(record, limit)
        return repository.observeRecords().first()
            .filter { it.record.id != record.record.id && it.record.createdAt <= record.record.createdAt }
            .sortedWith(compareBy({ it.record.createdAt }, { it.record.id }))
            .take(minOf(limit, DEMO_COUNT))
    }

    private suspend fun isOn(): Boolean = withContext(Dispatchers.IO) { File(app.filesDir, FLAG_FILE).exists() }

    companion object {
        const val FLAG_FILE = "debug_related_finder_on"
        const val DEMO_COUNT = 3
    }
}
