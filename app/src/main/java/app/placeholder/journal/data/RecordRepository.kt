package app.placeholder.journal.data

import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.data.db.CategoryEntity
import app.placeholder.journal.data.db.RecordEntity
import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.data.model.RecordWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The only data entry point for the UI. Deliberately a plain class — no interface until a second impl exists. */
class RecordRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeRecords(): Flow<List<RecordWithCategory>> = db.recordDao().observeAll()

    fun observeRecord(id: String): Flow<RecordWithCategory?> = db.recordDao().observe(id)

    fun observeCategories(): Flow<List<CategoryEntity>> = db.categoryDao().observeAll()

    suspend fun getRecord(id: String): RecordEntity? = db.recordDao().get(id)

    /** Creates a record and returns its id. */
    suspend fun create(text: String, emotion: Emotion?, categoryId: String?): String {
        val t = now()
        val record = RecordEntity(
            id = newId(),
            text = text.trimEnd(),
            createdAt = t,
            updatedAt = t,
            emotion = emotion,
            categoryId = categoryId,
            photoPath = null,
        )
        db.recordDao().insert(record)
        return record.id
    }

    suspend fun update(id: String, text: String, emotion: Emotion?, categoryId: String?) {
        val current = db.recordDao().get(id) ?: return
        db.recordDao().update(
            current.copy(text = text.trimEnd(), emotion = emotion, categoryId = categoryId, updatedAt = now()),
        )
    }

    suspend fun delete(id: String) = db.recordDao().delete(id)
}
