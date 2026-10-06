package app.oneulmundeuk

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.resurface.DateBasedResurfacedRecordSelector
import app.oneulmundeuk.ui.home.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Real Room Flow: create → delete brings Home back to the no-records empty state. */
@RunWith(AndroidJUnit4::class)
class HomeEmptyStateTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
    private val zone = ZoneId.systemDefault()
    private val selector = DateBasedResurfacedRecordSelector()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = RecordRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home() = HomeViewModel.state(repo.observeRecords().first(), selector, LocalDate.now(zone), zone)

    @Test
    fun emptyThenRecordThenDeleteAll() = runTest {
        assertTrue(home().noRecords)
        val id = repo.create("한 줄", null, null)
        assertFalse(home().noRecords)
        repo.delete(id)
        assertTrue(home().noRecords)
    }
}
