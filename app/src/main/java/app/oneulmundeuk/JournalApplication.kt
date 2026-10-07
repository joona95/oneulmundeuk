package app.oneulmundeuk

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.oneulmundeuk.data.draft.DataStoreDraftStore
import app.oneulmundeuk.data.draft.DraftStore
import app.oneulmundeuk.data.settings.SettingsStore
import app.oneulmundeuk.related.model.AndroidNetworkCheck
import app.oneulmundeuk.related.model.ModelInstaller
import app.oneulmundeuk.related.model.RelatedModels
import app.oneulmundeuk.related.model.SemanticGate
import app.oneulmundeuk.related.model.freeBytesAt
import app.oneulmundeuk.related.model.modelRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import app.oneulmundeuk.data.RecordRepository
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.related.RecordChangeListener
import app.oneulmundeuk.related.RelatedInvalidator
import app.oneulmundeuk.related.RelatedRepository
import app.oneulmundeuk.related.RelatedRuntime
import app.oneulmundeuk.related.RelatedStore
import app.oneulmundeuk.related.SemanticEnvironment
import app.oneulmundeuk.related.createRelatedRuntime
import app.oneulmundeuk.resurface.DateBasedResurfacedRecordSelector
import app.oneulmundeuk.resurface.ResurfacedRecordSelector
import app.oneulmundeuk.search.ExploreSearch
import app.oneulmundeuk.search.RoomSearchStorage
import app.oneulmundeuk.search.SemanticSearch

/** Manual DI: one small container instead of a DI framework. */
class AppContainer(app: Application) {
    val database: AppDatabase by lazy { AppDatabase.create(app) }
    /**
     * Per build type (src/debug, src/release). Release (and debug without the flag file): the real runtime — e5 record
     * embeddings once [semanticInferenceAllowed]; no results shown yet. Debug flag file: fake models (M6-4 debug fake).
     */
    val relatedRuntime: RelatedRuntime by lazy {
        createRelatedRuntime(
            app,
            SemanticEnvironment(
                database = { database },
                gate = semanticInferenceAllowed,
                refreshModels = { modelInstaller.refresh() },
                verifiedFile = modelInstaller::verifiedFile,
                scope = appScope,
            ),
        )
    }
    /** Keeps related-record caches consistent after each committed write (M6-2). */
    val recordChangeListener: RecordChangeListener by lazy {
        RelatedInvalidator(
            database,
            analysisEnabled = { relatedRuntime.analysisEnabled },
            afterChange = { relatedRuntime.onRecordsChanged() },
        )
    }
    val repository: RecordRepository by lazy { RecordRepository(database, changeListener = recordChangeListener) }
    /** Stored related results for the UI; the active pipeline version stays inside [RelatedStore]. */
    val relatedRepository: RelatedRepository by lazy {
        RelatedRepository(RelatedStore(database.relatedDao(), relatedRuntime.activePipelineVersion), database.recordDao())
    }
    /**
     * Explore "과거의 나에게 물어보세요": e5 semantic search + deterministic date routing, no LLM judge.
     * Without a local e5 (release today) only pure date questions are answered; others show "준비하고 있어요".
     */
    val semanticSearch: SemanticSearch by lazy { ExploreSearch(RoomSearchStorage(database), relatedRuntime.textEmbedder) }
    /**
     * Small local key-value store (Preferences DataStore, files/datastore/local_prefs.preferences_pb) — one instance per
     * process. Today: the new-record draft only; later settings can live here too. Excluded from backup / transfer.
     */
    val preferences: DataStore<Preferences> by lazy { PreferenceDataStoreFactory.create { app.preferencesDataStoreFile("local_prefs") } }
    /** The one in-progress new record (never in Room, so never a record anywhere in the app). */
    val recordDraftStore: DraftStore by lazy { DataStoreDraftStore(preferences) }
    /** Settings screen values (marker shape, 다시 만나기 알림, 관련된 생각) — same file, own keys. */
    val settingsStore: SettingsStore by lazy { SettingsStore(preferences) }
    /** App-lifetime work that must outlive a screen (the model download). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /**
     * 관련된 생각 model bundle (e5 + Qwen) in noBackupFilesDir/models. fetcher = null: no model host is fixed yet, so
     * 모델 받기 is refused as "source not configured" (no INTERNET permission, no fake download).
     */
    val modelInstaller: ModelInstaller by lazy {
        ModelInstaller(modelRoot(app), RelatedModels.BUNDLE, fetcher = null, AndroidNetworkCheck(app), { freeBytesAt(app.noBackupFilesDir) }, appScope)
    }
    /**
     * [SemanticGate]: 관련된 생각 ON AND models Ready. The production runtime reads it before queueing, loading or
     * running a model (the debug fake ignores it).
     */
    val semanticInferenceAllowed: Flow<Boolean> by lazy {
        combine(settingsStore.settings, modelInstaller.state) { s, m -> SemanticGate.allows(s.relatedEnabled, m) }
    }
    /** Home "다시 만난 생각" (date-based for now; swappable later). */
    val resurfacer: ResurfacedRecordSelector = DateBasedResurfacedRecordSelector()
}

class JournalApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.relatedRuntime.start()
    }
}
