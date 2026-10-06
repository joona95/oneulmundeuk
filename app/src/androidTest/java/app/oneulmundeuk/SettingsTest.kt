package app.oneulmundeuk

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.CategoryPolicy
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.draft.DataStoreDraftStore
import app.oneulmundeuk.data.draft.RecordDraft
import app.oneulmundeuk.data.settings.AppSettings
import app.oneulmundeuk.data.settings.RelatedThoughtsStatus
import app.oneulmundeuk.data.settings.SettingsStore
import app.oneulmundeuk.ui.components.MarkerShape
import app.oneulmundeuk.ui.settings.CategoryManageViewModel
import app.oneulmundeuk.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.io.File

/** Settings MVP on real Room (in-memory, production seed) + a real DataStore file + the real ViewModels. */
@RunWith(AndroidJUnit4::class)
class SettingsTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DataStore<Preferences>
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
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(context.cacheDir, "settings-test-${System.nanoTime()}.preferences_pb")
        prefs = PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.Main) { stores.forEach { it.clear() } }
        scope.cancel()
        db.close()
    }

    private inline fun <reified VM : androidx.lifecycle.ViewModel> vm(crossinline make: () -> VM): VM = runBlocking(Dispatchers.Main) {
        val store = ViewModelStore().also { stores += it }
        ViewModelProvider(store, viewModelFactory { initializer { make() } })[VM::class.java]
    }

    private fun <T> main(block: suspend () -> T): T = runBlocking(Dispatchers.Main) { withTimeout(5_000) { block() } }

    // ── settings persistence ──

    @Test
    fun defaultsThenEveryValuePersists() = runBlocking {
        val store = SettingsStore(prefs)
        assertEquals(AppSettings(), store.settings.first()) // 동글 · 알림 OFF · 관련된 생각 OFF

        store.setMarkerShape(MarkerShape.Heart)
        store.setReminderEnabled(true)
        store.setRelatedEnabled(true)

        // a new reader over the same file (what the next app start sees)
        val again = SettingsStore(prefs).settings.first()
        assertEquals(AppSettings(MarkerShape.Heart, reminderEnabled = true, relatedEnabled = true), again)

        store.setReminderEnabled(false)
        assertEquals(false, SettingsStore(prefs).settings.first().reminderEnabled)
    }

    @Test
    fun settingsAndDraftKeepSeparateKeys() = runBlocking {
        val settings = SettingsStore(prefs)
        val drafts = DataStoreDraftStore(prefs)
        settings.setMarkerShape(MarkerShape.Star)
        drafts.save(RecordDraft("초안", null, null))
        drafts.clear() // clearing the draft must not reset settings
        assertEquals(MarkerShape.Star, settings.settings.first().markerShape)
    }

    @Test
    fun relatedOnWithoutModelShowsNotDownloaded() {
        val store = SettingsStore(prefs)
        val vm = vm { SettingsViewModel(repo, store) }
        assertEquals(RelatedThoughtsStatus.OFF, main { vm.state.first { it.loaded } }.relatedStatus)
        main { vm.setRelatedEnabled(true) }
        assertEquals(RelatedThoughtsStatus.MODEL_NOT_DOWNLOADED, main { vm.state.first { it.settings.relatedEnabled } }.relatedStatus)
    }

    @Test
    fun settingsShowsActiveCategoriesOnly() {
        val store = SettingsStore(prefs)
        val vm = vm { SettingsViewModel(repo, store) }
        assertEquals(listOf("회사", "일상", "취미", "관계", "기타"), main { vm.state.first { it.loaded } }.categories.map { it.name })
        val daily = runBlocking { repo.observeCategories().first() }.single { it.name == "일상" }
        runBlocking { repo.archiveCategory(daily.id) }
        assertEquals(listOf("회사", "취미", "관계", "기타"), main { vm.state.first { s -> s.categories.none { it.name == "일상" } } }.categories.map { it.name })
    }

    // ── 카테고리 관리 ──

    @Test
    fun addTrimsAndRefusesBlankDuplicateAndArchivedName() {
        val vm = vm { CategoryManageViewModel(repo) }
        main { vm.state.first { it.categories.isNotEmpty() } }

        main { vm.onInputChange("   ") }
        assertTrue(!vm.state.value.canAdd) // blank: the button is off
        main { vm.add() }

        main { vm.onInputChange("  운동  ") }
        main { vm.add(); vm.state.first { s -> s.categories.any { it.name == "운동" } } }
        assertEquals("", vm.state.value.input)
        assertEquals("운동", vm.state.value.categories.last().name) // trimmed, appended last

        main { vm.onInputChange("회사"); vm.add(); vm.state.first { it.message != null } }
        assertEquals(CategoryPolicy.NAME_DUPLICATE, vm.state.value.message)

        val work = runBlocking { repo.observeCategories().first() }.single { it.name == "회사" }
        runBlocking { repo.archiveCategory(work.id) }
        main { vm.onInputChange("회사"); vm.add(); vm.state.first { it.message == CategoryPolicy.NAME_USED_BEFORE } }
        assertEquals(1, runBlocking { repo.observeCategories().first() }.count { it.name == "회사" }) // no second row, no crash
    }

    @Test
    fun deleteArchivesAfterConfirmationAndKeepsRecords() {
        val vm = vm { CategoryManageViewModel(repo) }
        val hobby = main { vm.state.first { it.categories.isNotEmpty() } }.categories.single { it.name == "취미" }
        val recordId = runBlocking { repo.create("자전거", null, hobby.id) }

        main { vm.requestDelete(hobby) }
        assertEquals(hobby, vm.state.value.pendingDelete)
        main { vm.dismissDelete() } // 취소: nothing happens
        assertNull(vm.state.value.pendingDelete)
        assertNull(runBlocking { repo.observeCategories().first() }.single { it.id == hobby.id }.archivedAt)

        main { vm.requestDelete(hobby); vm.confirmDelete(); vm.state.first { s -> s.categories.none { it.id == hobby.id } } }
        val row = runBlocking { repo.observeCategories().first() }.single { it.id == hobby.id } // row kept
        assertNotNull(row.archivedAt)
        val record = runBlocking { repo.observeRecords().first() }.single { it.record.id == recordId }
        assertEquals(hobby.id, record.record.categoryId)
        assertEquals("취미", record.categoryName)
    }
}
