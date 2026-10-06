package app.oneulmundeuk

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.draft.DraftStore
import app.oneulmundeuk.data.draft.RecordDraft
import app.oneulmundeuk.related.RelatedRepository
import app.oneulmundeuk.related.RelatedStore
import app.oneulmundeuk.search.RoomSearchStorage
import app.oneulmundeuk.ui.editor.RecordEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Category "삭제" = archive, on a real (in-memory) Room db with the production seed and FK callbacks. */
@RunWith(AndroidJUnit4::class)
class CategoryArchiveTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
    private lateinit var related: RelatedRepository
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.SEED_DEFAULT_CATEGORIES)
            .addCallback(AppDatabase.ENFORCE_FOREIGN_KEYS)
            .allowMainThreadQueries()
            .build()
        repo = RecordRepository(db)
        related = RelatedRepository(RelatedStore(db.relatedDao(), null), db.recordDao())
        runBlocking { db.categoryDao().insert(CategoryEntity("dev", "개발", 10, 0)) } // an old-style category
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.Main) { stores.forEach { it.clear() } }
        db.close()
    }

    private fun editor(recordId: String?, drafts: DraftStore? = null): RecordEditorViewModel = runBlocking(Dispatchers.Main) {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider(store, viewModelFactory {
            initializer { RecordEditorViewModel(repo, related, recordId, graceWindowMs = 0, drafts = drafts, draftDebounceMs = 0) }
        })[RecordEditorViewModel::class.java]
        withTimeout(5_000) { vm.state.first { it.loaded } }
        vm
    }

    private fun chips(vm: RecordEditorViewModel): List<String> =
        runBlocking(Dispatchers.Main) { withTimeout(5_000) { vm.categories.first { it.isNotEmpty() } }.map { it.name } }

    private fun categories() = runBlocking { repo.observeCategories().first() }

    @Test
    fun freshDatabaseSeedsFiveDefaults() {
        assertEquals(listOf("회사", "일상", "취미", "관계", "기타"), categories().filter { it.id != "dev" }.map { it.name })
    }

    @Test
    fun archiveKeepsRowAndRecordsAndSearchCorpus() = runBlocking {
        val id = repo.create("개발 공부 기록", null, "dev")
        repo.archiveCategory("dev")

        val dev = categories().single { it.id == "dev" } // the row is still there
        assertNotNull(dev.archivedAt)
        val record = repo.observeRecords().first().single()
        assertEquals("dev", record.record.categoryId) // record untouched
        assertEquals("개발", record.categoryName) // and still shows its category

        val storage = RoomSearchStorage(db) // Explore corpus: archived-category records stay searchable
        assertEquals(listOf(id), storage.allRecords().map { it.id })
        assertEquals(listOf(id), storage.recordIdsInCategory("dev")) // category hint over history still works

        val before = dev.archivedAt
        repo.archiveCategory("dev") // deleting again changes nothing
        assertEquals(before, categories().single { it.id == "dev" }.archivedAt)
    }

    @Test
    fun createOffersOnlyActiveCategories() {
        runBlocking { repo.archiveCategory("dev") }
        val vm = editor(null)
        assertEquals(listOf("회사", "일상", "취미", "관계", "기타"), chips(vm))
    }

    @Test
    fun editKeepsArchivedCategoryUntilChanged() {
        val id = runBlocking { repo.create("예전 개발 기록", null, "dev") }
        runBlocking { repo.archiveCategory("dev") }
        val vm = editor(id)
        assertEquals("dev", vm.state.value.categoryId)
        assertTrue("개발" in chips(vm)) // current value stays visible / selected

        // unchanged → saved as is (archived value kept)
        runBlocking(Dispatchers.Main) { vm.onTextChange("예전 개발 기록 (수정)"); vm.save(); withTimeout(5_000) { vm.state.first { it.saved } } }
        assertEquals("dev", runBlocking { db.recordDao().get(id) }?.categoryId)

        // another editor session: switch to an active category → the archived one is no longer offered
        val vm2 = editor(id)
        val work = categories().single { it.name == "회사" }.id
        runBlocking(Dispatchers.Main) { vm2.onCategoryChange(work) }
        runBlocking(Dispatchers.Main) { withTimeout(5_000) { vm2.categories.first { cs -> cs.none { it.id == "dev" } } } }
        runBlocking(Dispatchers.Main) { vm2.save(); withTimeout(5_000) { vm2.state.first { it.saved } } }
        assertEquals(work, runBlocking { db.recordDao().get(id) }?.categoryId)
    }

    @Test
    fun restoredDraftDropsArchivedCategory() {
        runBlocking { repo.archiveCategory("dev") }
        val drafts = object : DraftStore {
            var saved: RecordDraft? = RecordDraft("초안", null, "dev")
            override suspend fun load() = saved
            override suspend fun save(draft: RecordDraft) { saved = draft }
            override suspend fun clear() { saved = null }
        }
        val vm = editor(null, drafts)
        assertEquals("초안", vm.state.value.text)
        assertNull(vm.state.value.categoryId)
    }
}
