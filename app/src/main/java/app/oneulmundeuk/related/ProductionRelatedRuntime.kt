package app.oneulmundeuk.related

import android.util.Log
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.related.e5.E5Embedder
import app.oneulmundeuk.related.e5.E5Purpose
import app.oneulmundeuk.related.model.ModelArtifact
import app.oneulmundeuk.related.model.RelatedModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** What a related runtime may use from the app container (passed to `createRelatedRuntime`). */
class SemanticEnvironment(
    val database: () -> AppDatabase,
    /** `SemanticGate`: 관련된 생각 ON AND model bundle Ready. */
    val gate: Flow<Boolean>,
    /** Re-read the installed model files (`ModelInstaller.refresh`). */
    val refreshModels: suspend () -> Unit,
    /** Installed + verified file of an artifact, else null (`ModelInstaller.verifiedFile`). */
    val verifiedFile: (ModelArtifact) -> File?,
    /** App-lifetime scope (background work, never the UI). */
    val scope: CoroutineScope,
)

/**
 * The real runtime (release; debug without the fake flag file): e5 INT8 from `noBackupFilesDir/models`, Related space
 * ([E5Purpose.RELATED]). See [E5RecordEmbeddingRuntime] for what it does and does not do yet.
 */
fun productionRelatedRuntime(env: SemanticEnvironment): RelatedRuntime = E5RecordEmbeddingRuntime(
    storage = RoomRecordEmbeddingStorage(env.database),
    gate = env.gate,
    prepare = env.refreshModels,
    relatedModelId = E5Embedder.modelIdFor(E5Purpose.RELATED),
    loader = EmbedderLoader {
        withContext(Dispatchers.IO) {
            val model = checkNotNull(env.verifiedFile(RelatedModels.E5)) { "e5 model not installed" }
            val tokenizer = checkNotNull(env.verifiedFile(RelatedModels.E5_TOKENIZER)) { "e5 tokenizer not installed" }
            val e5 = E5Embedder.load(model, tokenizer)
            LoadedEmbedder(e5.textEmbedder(E5Purpose.RELATED), e5::close)
        }
    },
    scope = env.scope,
    log = { message, error -> Log.w("RelatedRuntime", message, error) },
)
