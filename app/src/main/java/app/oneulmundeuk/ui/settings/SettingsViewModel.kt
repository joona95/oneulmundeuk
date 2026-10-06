package app.oneulmundeuk.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oneulmundeuk.data.CategoryPolicy
import app.oneulmundeuk.data.NewCategoryName
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.settings.AppSettings
import app.oneulmundeuk.data.settings.RelatedThoughtsStatus
import app.oneulmundeuk.data.settings.SettingsStore
import app.oneulmundeuk.data.settings.relatedThoughtsStatus
import app.oneulmundeuk.related.model.ModelInstallState
import app.oneulmundeuk.related.model.ModelInstaller
import app.oneulmundeuk.related.model.ModelProblem
import app.oneulmundeuk.ui.components.MarkerShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loaded: Boolean = false,
    /** Active categories only (chips on the Settings screen). */
    val categories: List<CategoryEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val relatedStatus: RelatedThoughtsStatus = RelatedThoughtsStatus.OFF,
    /** The model bundle on disk (independent of the switch: OFF keeps installed models). */
    val install: ModelInstallState = ModelInstallState.NotInstalled,
    /** Last refusal / failure of 모델 받기 / 삭제. */
    val problem: ModelProblem? = null,
    /** 모델 받기 can work (manifest complete + network implementation). False → button disabled, "준비 중" line. */
    val downloadAvailable: Boolean = false,
    /** Bundle size for the "필요한 저장 공간" line (only shown when [downloadAvailable]). */
    val modelBytes: Long = 0L,
)

/**
 * Settings. Values live in [SettingsStore] (DataStore) and apply app-wide right away (the marker shape through the theme).
 * 관련된 생각: the switch is the user's wish; [ModelInstaller] owns the files. Status = [relatedThoughtsStatus] of both.
 * The debug fake runtime is a test tool, not an installed model — it never makes this READY.
 */
class SettingsViewModel(
    repository: RecordRepository,
    private val store: SettingsStore,
    private val models: ModelInstaller,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        repository.observeCategories(),
        store.settings,
        models.state,
        models.problem,
    ) { categories, settings, install, problem ->
        SettingsUiState(
            loaded = true,
            categories = CategoryPolicy.active(categories),
            settings = settings,
            relatedStatus = relatedThoughtsStatus(
                enabled = settings.relatedEnabled,
                modelInstalled = install is ModelInstallState.Ready,
                downloading = install is ModelInstallState.Downloading,
            ),
            install = install,
            problem = problem,
            downloadAvailable = models.downloadAvailable,
            modelBytes = models.totalBytes ?: 0L,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { models.refresh() } // also recovers from an interrupted download (keeps a valid .part)
    }

    fun setMarkerShape(shape: MarkerShape) { viewModelScope.launch { store.setMarkerShape(shape) } }
    fun setReminderEnabled(enabled: Boolean) { viewModelScope.launch { store.setReminderEnabled(enabled) } }
    fun setRelatedEnabled(enabled: Boolean) { viewModelScope.launch { store.setRelatedEnabled(enabled) } }

    /** 모델 받기: refused (with a [ModelProblem]) unless the source is set, on Wi-Fi, with enough space. */
    fun downloadModels() { models.startDownload() }

    /** AI 모델 삭제: model files only, then 관련된 생각 OFF. Records and related results stay. Failure → DELETE_FAILED. */
    fun deleteModels() {
        viewModelScope.launch { if (models.deleteAll()) store.setRelatedEnabled(false) }
    }
}

data class CategoryManageUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val input: String = "",
    /** Why the last add was refused (shown under the field); cleared on typing. */
    val message: String? = null,
    val adding: Boolean = false,
    /** Waiting for the delete confirmation. */
    val pendingDelete: CategoryEntity? = null,
) {
    val canAdd: Boolean get() = input.isNotBlank() && !adding
}

/** 카테고리 관리: active categories, add (trim / unique), delete (= archive, confirmed). No restore, no reorder / rename. */
class CategoryManageViewModel(private val repository: RecordRepository) : ViewModel() {
    private data class Form(val input: String = "", val message: String? = null, val adding: Boolean = false, val pendingDelete: CategoryEntity? = null)

    private val form = MutableStateFlow(Form())

    val state: StateFlow<CategoryManageUiState> = combine(repository.observeCategories(), form) { categories, f ->
        CategoryManageUiState(CategoryPolicy.active(categories), f.input, f.message, f.adding, f.pendingDelete)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManageUiState())

    fun onInputChange(text: String) = form.update { it.copy(input = text, message = null) }

    fun add() {
        val f = form.value
        if (f.input.isBlank() || f.adding) return
        form.update { it.copy(adding = true) }
        viewModelScope.launch {
            val result = repository.addCategory(f.input)
            form.update {
                when (result) {
                    is NewCategoryName.Ok -> it.copy(input = "", message = null, adding = false)
                    NewCategoryName.Blank -> it.copy(adding = false)
                    NewCategoryName.Duplicate -> it.copy(message = CategoryPolicy.NAME_DUPLICATE, adding = false)
                    NewCategoryName.UsedBefore -> it.copy(message = CategoryPolicy.NAME_USED_BEFORE, adding = false)
                }
            }
        }
    }

    fun requestDelete(category: CategoryEntity) = form.update { it.copy(pendingDelete = category) }

    /** 취소, outside tap or back on the dialog. */
    fun dismissDelete() = form.update { it.copy(pendingDelete = null) }

    /** 삭제 = archive (CategoryPolicy): gone from active lists at once, kept for past records. */
    fun confirmDelete() {
        val target = form.value.pendingDelete ?: return
        form.update { it.copy(pendingDelete = null) }
        viewModelScope.launch { repository.archiveCategory(target.id) }
    }
}
