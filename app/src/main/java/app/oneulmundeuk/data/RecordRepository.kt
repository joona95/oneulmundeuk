package app.oneulmundeuk.data

import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.db.RecordEntity
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.related.NoOpRecordChangeListener
import app.oneulmundeuk.related.RecordChangeListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import android.database.sqlite.SQLiteConstraintException
import java.util.UUID

/**
 * The only data entry point for the UI. Deliberately a plain class — no interface until a second impl exists.
 * After each committed write it tells [changeListener] (M6: related-record analysis is queued from there).
 * The listener can never fail or delay-fail a write: anything it throws is swallowed.
 */
class RecordRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val changeListener: RecordChangeListener = NoOpRecordChangeListener,
) {
    fun observeRecords(): Flow<List<RecordWithCategory>> = db.recordDao().observeAll()

    fun observeRecord(id: String): Flow<RecordWithCategory?> = db.recordDao().observe(id)

    /** Every category, archived ones included (past records still use them) — pick a view with [CategoryPolicy]. */
    fun observeCategories(): Flow<List<CategoryEntity>> = db.categoryDao().observeAll()

    /** 카테고리 추가: trimmed, checked by [CategoryPolicy.checkNewName]; appended after the existing ones. */
    suspend fun addCategory(input: String): NewCategoryName {
        val all = db.categoryDao().observeAll().first()
        val check = CategoryPolicy.checkNewName(input, all)
        if (check !is NewCategoryName.Ok) return check
        return try {
            db.categoryDao().insert(CategoryEntity(newId(), check.name, (all.maxOfOrNull { it.sortOrder } ?: -1) + 1, now()))
            check
        } catch (e: SQLiteConstraintException) {
            NewCategoryName.Duplicate // added concurrently with the same name: refuse, never crash
        }
    }

    /** Category "삭제": archive only. The row and every record that uses it stay unchanged. */
    suspend fun archiveCategory(id: String) = db.categoryDao().archive(id, now())

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
        tellListener { onRecordCreated(record.id) }
        return record.id
    }

    suspend fun update(id: String, text: String, emotion: Emotion?, categoryId: String?) {
        val current = db.recordDao().get(id) ?: return
        val newText = text.trimEnd()
        db.recordDao().update(
            current.copy(text = newText, emotion = emotion, categoryId = categoryId, updatedAt = now()),
        )
        tellListener { onRecordUpdated(id, textChanged = newText != current.text) }
    }

    suspend fun delete(id: String) {
        db.recordDao().delete(id)
        tellListener { onRecordDeleted(id) }
    }

    /** Runs after the write has committed. Cancellation still propagates; any other failure is ignored. */
    private suspend fun tellListener(block: suspend RecordChangeListener.() -> Unit) {
        try {
            changeListener.block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // best effort: related analysis must never break saving
        }
    }
}
