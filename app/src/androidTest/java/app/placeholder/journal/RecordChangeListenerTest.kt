package app.placeholder.journal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.placeholder.journal.data.RecordRepository
import app.placeholder.journal.data.db.AppDatabase
import app.placeholder.journal.data.model.Emotion
import app.placeholder.journal.related.RecordChangeListener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M6-1: the repository tells its RecordChangeListener after each committed write, and a failing listener never
 * fails or undoes a save / edit / delete.
 */
@RunWith(AndroidJUnit4::class)
class RecordChangeListenerTest {
    private lateinit var db: AppDatabase
    private var clock = 1_000L

    private class Recording(private val fail: Boolean = false) : RecordChangeListener {
        val events = mutableListOf<String>()
        override suspend fun onRecordCreated(recordId: String) { events += "created:$recordId"; if (fail) error("boom") }
        override suspend fun onRecordUpdated(recordId: String, textChanged: Boolean) { events += "updated:$recordId:$textChanged"; if (fail) error("boom") }
        override suspend fun onRecordDeleted(recordId: String) { events += "deleted:$recordId"; if (fail) error("boom") }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun toldAfterCreateUpdateDelete() = runTest {
        val listener = Recording()
        val repo = RecordRepository(db, now = { clock++ }, changeListener = listener)
        val id = repo.create("처음 생각", null, null)
        assertEquals(listOf("created:$id"), listener.events)
        assertEquals("처음 생각", repo.getRecord(id)?.text) // the write committed before the listener ran

        repo.update(id, "바뀐 생각", Emotion.CALM, null)
        repo.update(id, "바뀐 생각  ", Emotion.SAD, null) // only emotion (trailing spaces are trimmed)
        repo.delete(id)
        assertEquals(listOf("created:$id", "updated:$id:true", "updated:$id:false", "deleted:$id"), listener.events)
    }

    @Test
    fun failingListenerNeverBreaksWrites() = runTest {
        val listener = Recording(fail = true)
        val repo = RecordRepository(db, now = { clock++ }, changeListener = listener)
        val id = repo.create("저장은 된다", null, null)
        assertEquals(1, repo.observeRecords().first().size)
        repo.update(id, "수정도 된다", null, null)
        assertEquals("수정도 된다", repo.getRecord(id)?.text)
        repo.delete(id)
        assertNull(repo.getRecord(id))
        assertEquals(3, listener.events.size)
    }

    @Test
    fun missingRecordUpdateTellsNothing() = runTest {
        val listener = Recording()
        RecordRepository(db, now = { clock++ }, changeListener = listener).update("none", "x", null, null)
        assertEquals(emptyList<String>(), listener.events)
    }
}
