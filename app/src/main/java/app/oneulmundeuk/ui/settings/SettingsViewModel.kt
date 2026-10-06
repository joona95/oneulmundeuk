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
)

/**
 * Settings. Values live in [SettingsStore] (DataStore) and apply app-wide right away (the marker shape through the theme).
 * [relatedModelInstalled]: always false until the model download step exists (TODO(related-model)) — so 관련된 생각 ON
 * shows "not downloaded"; the debug fake runtime is a test tool, not an installed model.
 */
class SettingsViewModel(
    repository: RecordRepository,
    private val store: SettingsStore,
    private val relatedModelInstalled: () -> Boolean = { false },
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(repository.observeCategories(), store.settings) { categories, settings ->
        SettingsUiState(
            loaded = true,
            categories = CategoryPolicy.active(categories),
            settings = settings,
            relatedStatus = relatedThoughtsStatus(settings.relatedEnabled, relatedModelInstalled()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setMarkerShape(shape: MarkerShape) { viewModelScope.launch { store.setMarkerShape(shape) } }
    fun setReminderEnabled(enabled: Boolean) { viewModelScope.launch { store.setReminderEnabled(enabled) } }
    fun setRelatedEnabled(enabled: Boolean) { viewModelScope.launch { store.setRelatedEnabled(enabled) } }
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
