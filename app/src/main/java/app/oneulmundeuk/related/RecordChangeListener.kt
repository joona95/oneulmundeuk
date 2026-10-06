package app.oneulmundeuk.related

/**
 * Told by `RecordRepository` AFTER a record write has committed (M6, docs/m6-related-design.md).
 * M6-1 uses [NoOpRecordChangeListener]; M6-2 queues background related-record analysis here.
 *
 * Contract: quick (no model loading, no inference, no network), never needed for the save to succeed —
 * the repository ignores anything it throws except cancellation. Saving never waits for related results.
 */
interface RecordChangeListener {
    suspend fun onRecordCreated(recordId: String)

    /** [textChanged] = the record text differs after trimming; emotion / category edits alone are false. */
    suspend fun onRecordUpdated(recordId: String, textChanged: Boolean)

    suspend fun onRecordDeleted(recordId: String)
}

object NoOpRecordChangeListener : RecordChangeListener {
    override suspend fun onRecordCreated(recordId: String) = Unit
    override suspend fun onRecordUpdated(recordId: String, textChanged: Boolean) = Unit
    override suspend fun onRecordDeleted(recordId: String) = Unit
}
