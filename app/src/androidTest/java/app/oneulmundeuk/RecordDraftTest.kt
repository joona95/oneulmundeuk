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
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.draft.DataStoreDraftStore
import app.oneulmundeuk.data.draft.RecordDraft
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.related.RelatedRepository
import app.oneulmundeuk.related.RelatedStore
import app.oneulmundeuk.resurface.DateBasedResurfacedRecordSelector
import app.oneulmundeuk.ui.editor.EditorState
import app.oneulmundeuk.ui.editor.RecordEditorViewModel
import app.oneulmundeuk.ui.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * New-record draft: Room (in memory) + the real DataStore draft store + the real editor ViewModel.
 * "Leaving the editor" = clearing its ViewModelStore (what popping the back stack entry does); a new store = re-entry
 * (also what activity / process recreation looks like to the editor: a fresh ViewModel over the same disk).
 */
@RunWith(AndroidJUnit4::class)
class RecordDraftTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecordRepository
    private lateinit var related: RelatedRepository
    private lateinit var prefsScope: CoroutineScope
    private lateinit var prefs: DataStore<Preferences>
    private lateinit var drafts: DataStoreDraftStore
    private val stores = mutableListOf<ViewModelStore>()
    /** Next id the repository hands out (null = random) — lets a test force a primary-key conflict on insert. */
    private var forcedId: String? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = RecordRepository(db, newId = { forcedId ?: java.util.UUID.randomUUID().toString() })
        related = RelatedRepository(RelatedStore(db.relatedDao(), null), db.recordDao())
        prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(context.cacheDir, "draft-test-${System.nanoTime()}.preferences_pb")
        prefs = PreferenceDataStoreFactory.create(scope = prefsScope) { file }
        drafts = DataStoreDraftStore(prefs)
        runBlocking { db.categoryDao().insert(CategoryEntity("c1", "커리어", 0, 0)) }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.Main) { stores.forEach { it.clear() } }
        prefsScope.cancel()
        if (db.isOpen) db.close()
    }

    /** Opens the editor (create mode when [recordId] is null) and waits until it is ready. */
    private fun open(recordId: String? = null): Pair<RecordEditorViewModel, ViewModelStore> = runBlocking(Dispatchers.Main) {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider(store, viewModelFactory {
            initializer { RecordEditorViewModel(repo, related, recordId, graceWindowMs = 0, drafts = drafts, draftDebounceMs = 0) }
        })[RecordEditorViewModel::class.java]
        withTimeout(5_000) { vm.state.first { it.loaded } }
        vm to store
    }

    private fun leave(store: ViewModelStore) = runBlocking(Dispatchers.Main) { store.clear() }

    private fun <T> main(block: suspend () -> T): T = runBlocking(Dispatchers.Main) { block() }

    private suspend fun RecordEditorViewModel.await(p: (EditorState) -> Boolean) = withTimeout(5_000) { state.first(p) }

    private fun write(vm: RecordEditorViewModel, text: String, emotion: Emotion? = null, category: String? = null) = main {
        vm.onTextChange(text); vm.onEmotionChange(emotion); vm.onCategoryChange(category)
    }

    private fun stored(): RecordDraft? = runBlocking { drafts.load() }

    // 1 · 2 · 13: back (system back and the X both call requestExit) with text → dialog; dialog back → only the dialog closes
    @Test
    fun backWithTextShowsDialogAndDialogBackKeepsEditor() {
        val (vm, _) = open()
        write(vm, "길게 쓰는 중", Emotion.CALM, "c1")
        main { vm.requestExit() }
        assertTrue(vm.state.value.exitConfirm)
        assertFalse(vm.state.value.exited)
        main { vm.dismissExitDialog() }
        val s = vm.state.value
        assertFalse(s.exitConfirm)
        assertFalse(s.exited)
        assertEquals("길게 쓰는 중", s.text)
        assertEquals(Emotion.CALM, s.emotion)
        assertEquals("c1", s.categoryId)
    }

    // 3: nothing written → closes at once, no draft
    @Test
    fun backWithNothingWrittenClosesWithoutDialog() {
        val (vm, _) = open()
        main { vm.requestExit(); vm.await { it.exited } }
        assertFalse(vm.state.value.exitConfirm)
        assertNull(stored())
    }

    // 4 · 5 · 10: 임시저장 → re-entry (fresh ViewModel) restores everything; more text → 임시저장 keeps the latest
    @Test
    fun keepDraftRestoresOnReentryAndUpdates() {
        val (vm, store) = open()
        write(vm, "첫 문장", Emotion.TIRED, "c1")
        main { vm.requestExit(); vm.keepDraftAndExit(); vm.await { it.exited } }
        leave(store)
        assertEquals(RecordDraft("첫 문장", Emotion.TIRED, "c1"), stored())

        val (again, store2) = open()
        assertEquals(Triple("첫 문장", Emotion.TIRED, "c1"), again.state.value.let { Triple(it.text, it.emotion, it.categoryId) })
        write(again, "첫 문장\n둘째 문장", Emotion.CALM, null)
        main { again.requestExit() }
        assertTrue(again.state.value.exitConfirm) // a restored draft asks again
        main { again.keepDraftAndExit(); again.await { it.exited } }
        leave(store2)
        assertEquals(RecordDraft("첫 문장\n둘째 문장", Emotion.CALM, null), stored())
    }

    // 6 · 7: 버리기 → no draft on re-entry, including a draft restored from before
    @Test
    fun discardDeletesDraftIncludingRestoredOne() {
        val (vm, store) = open()
        write(vm, "남길 문장")
        main { vm.keepDraftAndExit(); vm.await { it.exited } }
        leave(store)

        val (restored, store2) = open()
        assertEquals("남길 문장", restored.state.value.text)
        main { restored.requestExit(); restored.discardAndExit(); restored.await { it.exited } }
        leave(store2)
        assertNull(stored())

        val (fresh, _) = open()
        assertEquals("", fresh.state.value.text)
    }

    // auto persistence (no explicit 임시저장): the latest state reaches disk; flush (ON_STOP) writes without waiting
    @Test
    fun typingIsPersistedWithoutExplicitSave() {
        val (vm, _) = open()
        write(vm, "자동으로 보존", Emotion.CALM, "c1")
        main { vm.flushDraft() }
        runBlocking { withTimeout(5_000) { while (stored() != RecordDraft("자동으로 보존", Emotion.CALM, "c1")) delay(20) } }
    }

    // 8 · 12: a successful save deletes the draft; the draft itself was never a record anywhere
    @Test
    fun successfulSaveDeletesDraftAndDraftIsNeverARecord() {
        val (vm, _) = open()
        write(vm, "저장할 생각")
        main { vm.flushDraft() }
        runBlocking { withTimeout(5_000) { while (stored() == null) delay(20) } }
        val beforeSave = runBlocking { repo.observeRecords().first() }
        assertTrue(beforeSave.isEmpty()) // not in Room → not in Home / Records / Explore / Related / resurfacing
        val zone = ZoneId.systemDefault()
        assertTrue(HomeViewModel.state(beforeSave, DateBasedResurfacedRecordSelector(), LocalDate.now(zone), zone).noRecords)

        main { vm.save(); vm.await { it.saved } }
        assertNull(stored())
        assertEquals(1, runBlocking { repo.observeRecords().first() }.size)
    }

    // 9: the DB write fails → the draft stays, the editor keeps the text
    @Test
    fun failedSaveKeepsDraft() {
        val (vm, _) = open()
        write(vm, "실패해도 남아야 함")
        main { vm.flushDraft() }
        runBlocking { withTimeout(5_000) { while (stored() == null) delay(20) } }
        // A real insert failure: the next record gets an id that already exists → @Insert(ABORT) throws.
        // (Closing an in-memory Room db does not work here: Room reopens it on the next write.)
        forcedId = "taken"
        runBlocking { repo.create("이미 있는 기록", null, null) }
        main {
            vm.save()
            withTimeout(5_000) { vm.state.first { !it.saving } }
        }
        assertFalse(vm.state.value.saved)
        assertEquals("실패해도 남아야 함", vm.state.value.text)
        assertEquals(RecordDraft("실패해도 남아야 함", null, null), stored())
        assertEquals(listOf("이미 있는 기록"), runBlocking { repo.observeRecords().first() }.map { it.record.text }) // nothing new saved
    }

    // 11: edit mode neither restores nor writes the create draft, and never asks on back
    @Test
    fun editModeIgnoresCreateDraft() {
        val (vm, store) = open()
        write(vm, "새 기록 초안")
        main { vm.keepDraftAndExit(); vm.await { it.exited } }
        leave(store)
        val id = runBlocking { repo.create("기존 기록", null, null) }

        val (edit, _) = open(id)
        assertEquals("기존 기록", edit.state.value.text)
        write(edit, "기존 기록 수정")
        main { edit.flushDraft(); edit.requestExit(); edit.await { it.exited } }
        assertFalse(edit.state.value.exitConfirm)
        assertEquals(RecordDraft("새 기록 초안", null, null), stored())
    }

    // a restored draft whose category was deleted meanwhile drops only the category (it would break the save)
    @Test
    fun restoredDraftDropsDeletedCategory() {
        runBlocking { drafts.save(RecordDraft("문장", null, "gone")) }
        val (vm, _) = open()
        assertEquals("문장", vm.state.value.text)
        assertNull(vm.state.value.categoryId)
    }
}
