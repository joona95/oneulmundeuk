package app.oneulmundeuk.related

import android.util.Log
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.related.e5.E5Embedder
import app.oneulmundeuk.related.e5.E5Purpose
import app.oneulmundeuk.related.model.ModelArtifact
import app.oneulmundeuk.related.model.RelatedModels
import app.oneulmundeuk.related.qwen.JudgeV1
import app.oneulmundeuk.related.qwen.LlamaCppQwenEngine
import app.oneulmundeuk.related.qwen.QwenJudge
import app.oneulmundeuk.related.qwen.QwenRuntimeSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    /** An APK asset's bytes (judge_v1 prompt). */
    val readAsset: (String) -> ByteArray,
    /** App-lifetime scope (background work, never the UI). */
    val scope: CoroutineScope,
)

/**
 * The real runtime (release; debug without the fake flag file): [LocalRelatedRuntime] with e5 INT8 (RELATED space,
 * `query: ` on both records) and Qwen3.5-2B judge_v1 through llama.cpp, both from `noBackupFilesDir/models`
 * (ModelInstaller-verified files only — nothing is packaged). The gate also requires [QwenRuntimeSupport]
 * (arm64-v8a + CPU features + library), checked only once the gate would otherwise open.
 */
fun productionRelatedRuntime(env: SemanticEnvironment): RelatedRuntime = LocalRelatedRuntime(
    storage = RoomRelatedRuntimeStorage(env.database),
    gate = env.gate.map { open -> open && QwenRuntimeSupport.available },
    prepare = env.refreshModels,
    embedderModelId = E5Embedder.modelIdFor(E5Purpose.RELATED),
    judgeModelId = QwenJudge.MODEL_ID,
    embedderLoader = ModelLoader {
        withContext(Dispatchers.IO) {
            val model = checkNotNull(env.verifiedFile(RelatedModels.E5)) { "e5 model not installed" }
            val tokenizer = checkNotNull(env.verifiedFile(RelatedModels.E5_TOKENIZER)) { "e5 tokenizer not installed" }
            val e5 = E5Embedder.load(model, tokenizer)
            LoadedModel(e5.textEmbedder(E5Purpose.RELATED), e5::close)
        }
    },
    judgeLoader = ModelLoader {
        withContext(Dispatchers.IO) {
            val prompt = JudgeV1.parse(env.readAsset(JudgeV1.ASSET)) // sha256-checked judge_v1
            val gguf = checkNotNull(env.verifiedFile(RelatedModels.QWEN)) { "qwen model not installed" }
            val engine = LlamaCppQwenEngine.load(gguf)
            LoadedModel<RelatedValueJudge>(QwenJudge(engine, prompt), engine::close)
        }
    },
    scope = env.scope,
    log = { message, error -> Log.w("RelatedRuntime", message, error) },
)
