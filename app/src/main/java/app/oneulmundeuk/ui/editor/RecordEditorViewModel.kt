package app.oneulmundeuk.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oneulmundeuk.data.CategoryPolicy
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.draft.DraftStore
import app.oneulmundeuk.data.draft.RecordDraft
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.related.RelatedRepository
import app.oneulmundeuk.ui.components.SaveFollowUp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class EditorState(
    val text: String = "",
    val emotion: Emotion? = null,
    val categoryId: String? = null,
    /** Original creation time when editing; null for a new record. */
    val createdAt: Long? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** True when the record just created is the only one (shows the first-record save message). */
    val firstRecord: Boolean = false,
    /** The record just created (new records only) — Related Memories opens with this id. */
    val savedRecordId: String? = null,
    /** Whether the save feedback ends in Related Memories instead of going back (decided within [RelatedGrace.WINDOW_MS]). */
    val followUp: SaveFollowUp = SaveFollowUp.None,
    /** "작성 중인 생각이 있어요" exit dialog is showing (new record with content, user tried to leave). */
    val exitConfirm: Boolean = false,
    /** The editor should close now (after the exit decision — and any draft write — has completed). */
    val exited: Boolean = false,
) {
    val canSave: Boolean get() = loaded && !saving && !saved && text.isNotBlank() // !saved: no double save during the feedback

    /** What the new-record draft should hold for this state; null = nothing worth keeping (see [RecordDraft.of]). */
    val draft: RecordDraft? get() = RecordDraft.of(text, emotion, categoryId)
}

/** What a back / X press does. Pure so it can be tested without UI. */
enum class EditorExit { Close, Confirm, Ignore }

fun editorExit(state: EditorState, draftEnabled: Boolean): EditorExit = when {
    state.saving || state.saved || state.exited -> EditorExit.Ignore // the save feedback / exit is already running
    draftEnabled && state.draft != null -> EditorExit.Confirm
    else -> EditorExit.Close
}

/**
 * Create when [recordId] is null, edit otherwise. Emotion and category are optional.
 * Saving never waits for related-record analysis: `saved` is set as soon as the write commits. Only afterwards, for a
 * new (not first) record, its stored related results are watched for the short grace window ([relatedWithin]); the
 * feedback then ends in Related Memories only if they are ready in time. The first record only changes the copy.
 *
 * New-record draft ([drafts], create mode only): restored on open (no prompt), auto-saved off the main thread with a
 * short debounce, flushed at once when the screen stops (background / process may die) and when the user picks
 * 임시저장. Cleared on 버리기 and right after a successful DB save — never when the save fails.
 */
class RecordEditorViewModel(
    private val repository: RecordRepository,
    private val related: RelatedRepository,
    private val recordId: String?,
    private val graceWindowMs: Long = RelatedGrace.WINDOW_MS,
    drafts: DraftStore? = null,
    private val draftDebounceMs: Long = DRAFT_DEBOUNCE_MS,
) : ViewModel() {
    val isEditing: Boolean = recordId != null

    /** Create mode only — an existing record is its own source of truth. */
    private val draftStore: DraftStore? = if (recordId == null) drafts else null
    private val draftLock = Mutex()
    /** Set (under [draftLock]) after 버리기 or a successful save: no later write may bring the draft back. */
    private var draftClosed = false
    /** False when the stored draft could not be read: then never clear it (it may still hold the user's text). */
    private var draftClearAllowed = true

    private val _state = MutableStateFlow(EditorState(loaded = recordId == null && draftStore == null))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Chips: active categories, plus an archived one only while it is this record's current value (Edit). */
    val categories: StateFlow<List<CategoryEntity>> =
        combine(repository.observeCategories(), _state.map { it.categoryId }.distinctUntilChanged(), CategoryPolicy::editorOptions)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val r = repository.getRecord(recordId)
                _state.update {
                    if (r == null) it.copy(loaded = true)
                    else it.copy(text = r.text, emotion = r.emotion, categoryId = r.categoryId, createdAt = r.createdAt, loaded = true)
                }
            }
        } else if (draftStore != null) {
            viewModelScope.launch { restoreDraftThenAutosave(draftStore) }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun restoreDraftThenAutosave(store: DraftStore) {
        val stored = try {
            store.load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            draftClearAllowed = false
            null
        }
        // A category removed or archived ("삭제") since the draft was written is not offered for a new record → drop it.
        val categoryId = stored?.categoryId?.takeIf { id -> CategoryPolicy.selectableForNew(repository.observeCategories().first(), id) }
        _state.update { s ->
            val untouched = s.text.isEmpty() && s.emotion == null && s.categoryId == null
            if (stored != null && untouched) s.copy(text = stored.text, emotion = stored.emotion, categoryId = categoryId, loaded = true)
            else s.copy(loaded = true)
        }
        // UI state updates at once; the disk write follows the last change by [draftDebounceMs].
        _state.map { it.draft }.distinctUntilChanged().debounce(draftDebounceMs).collect { writeDraft(it) }
    }

    /** Writes (or clears, for null) the draft. Serialized; never after [draftClosed]; best effort (never crashes the editor). */
    private suspend fun writeDraft(draft: RecordDraft?) {
        val store = draftStore ?: return
        draftLock.withLock {
            if (draftClosed) return
            try {
                if (draft != null) store.save(draft) else if (draftClearAllowed) store.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort: the next change / flush tries again
            }
        }
    }

    private suspend fun closeDraft() {
        val store = draftStore ?: return
        draftLock.withLock {
            draftClosed = true
            try {
                store.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** Screen stopped (app to background, another screen on top): write the latest state now, skipping the debounce. */
    fun flushDraft() {
        if (draftStore == null || !_state.value.loaded) return
        val draft = _state.value.draft
        viewModelScope.launch { withContext(NonCancellable) { writeDraft(draft) } }
    }

    /** Top-bar X, system back, predictive back. */
    fun requestExit() {
        val s = _state.value
        when (editorExit(s, draftEnabled = draftStore != null)) {
            EditorExit.Ignore -> Unit
            EditorExit.Confirm -> _state.update { it.copy(exitConfirm = true) }
            EditorExit.Close -> {
                if (draftStore == null || !s.loaded) {
                    _state.update { it.copy(exited = true) }
                } else {
                    // Nothing worth keeping (e.g. the restored text was erased): make sure no stale draft is left.
                    viewModelScope.launch {
                        withContext(NonCancellable) { writeDraft(null) }
                        _state.update { it.copy(exited = true) }
                    }
                }
            }
        }
    }

    /** Outside tap / system back on the dialog: only the dialog closes; the editor and its text stay. */
    fun dismissExitDialog() = _state.update { it.copy(exitConfirm = false) }

    /** 임시저장: the latest state is on disk before the editor closes. */
    fun keepDraftAndExit() {
        val draft = _state.value.draft
        _state.update { it.copy(exitConfirm = false) }
        viewModelScope.launch {
            withContext(NonCancellable) { writeDraft(draft) }
            _state.update { it.copy(exited = true) }
        }
    }

    /** 버리기: the draft (including one restored from before) is deleted, then the editor closes. */
    fun discardAndExit() {
        _state.update { it.copy(exitConfirm = false) }
        viewModelScope.launch {
            withContext(NonCancellable) { closeDraft() }
            _state.update { it.copy(exited = true) }
        }
    }

    fun onTextChange(text: String) = _state.update { it.copy(text = text) }
    fun onEmotionChange(emotion: Emotion?) = _state.update { it.copy(emotion = emotion) }
    fun onCategoryChange(categoryId: String?) = _state.update { it.copy(categoryId = categoryId) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            var first = false
            var newId: String? = null
            if (recordId == null) {
                newId = try {
                    repository.create(s.text, s.emotion, s.categoryId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Not saved: stay in the editor with the text, and keep the draft (it is the only copy).
                    _state.update { it.copy(saving = false) }
                    return@launch
                }
                // Saved → the draft is done (before the save feedback starts).
                closeDraft()
                // Read after the write has committed; the save itself is never delayed by the feedback UI.
                first = repository.observeRecords().first().size == 1
            } else {
                repository.update(recordId, s.text, s.emotion, s.categoryId)
            }
            val followUp = followUpAfterSave(isNewRecord = newId != null, firstRecord = first)
            _state.update { it.copy(saving = false, saved = true, firstRecord = first, savedRecordId = newId, followUp = followUp) }
            if (followUp == SaveFollowUp.Waiting && newId != null) {
                val decided = relatedWithin(related.observeRelated(newId), graceWindowMs)
                _state.update { it.copy(followUp = decided) }
            }
        }
    }

    companion object {
        const val DRAFT_DEBOUNCE_MS = 400L
    }
}
