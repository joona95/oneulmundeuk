package app.oneulmundeuk

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.model.Emotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = RecordRepository(db, now = { clock })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createListUpdateDelete() = runTest {
        db.categoryDao().insert(CategoryEntity("c1", "커리어", 0, 0))

        val id = repo.create("요즘 성장하고 있는지 모르겠다.  ", Emotion.SO_SO, "c1")
        clock = 2_000L
        repo.create("야근하고 들어오니 11시.", Emotion.TIRED, null)

        val list = repo.observeRecords().first()
        assertEquals(2, list.size)
        assertEquals("야근하고 들어오니 11시.", list[0].record.text) // newest first
        assertEquals("커리어", list[1].categoryName)
        assertEquals("요즘 성장하고 있는지 모르겠다.", list[1].record.text) // trailing space trimmed

        // Emotion is stored as its explicit stable key
        db.openHelper.readableDatabase.query("SELECT emotion FROM records WHERE id = ?", arrayOf(id)).use {
            it.moveToFirst()
            assertEquals("so_so", it.getString(0))
        }

        clock = 3_000L
        repo.update(id, "수정한 문장", Emotion.CALM, null)
        val updated = repo.observeRecord(id).first()!!
        assertEquals("수정한 문장", updated.record.text)
        assertEquals(Emotion.CALM, updated.record.emotion)
        assertNull(updated.categoryName)
        assertEquals(1_000L, updated.record.createdAt)
        assertEquals(3_000L, updated.record.updatedAt)

        repo.delete(id)
        assertNull(repo.observeRecord(id).first())
        assertTrue(repo.observeRecords().first().none { it.record.id == id })
    }

    @Test
    fun deletingCategoryKeepsRecordAsUncategorized() = runTest {
        db.categoryDao().insert(CategoryEntity("c1", "취미", 0, 0))
        val id = repo.create("자전거", Emotion.CALM, "c1")
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = 'c1'")
        val r = repo.observeRecord(id).first()!!
        assertNull(r.record.categoryId)
        assertNull(r.categoryName)
    }
}
