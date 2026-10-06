package app.oneulmundeuk.related.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Install state of the whole bundle, derived from the files on disk (+ a running download). */
sealed interface ModelInstallState {
    /** Nothing, a partial bundle, a wrong size / hash, or another version on disk. */
    data object NotInstalled : ModelInstallState

    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelInstallState {
        val fraction: Float get() = if (totalBytes <= 0) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    /** Every artifact verified (size + sha256 + current version). */
    data class Ready(val bytesOnDisk: Long) : ModelInstallState
}

/** Why the last action did not happen. Settings maps these to plain sentences (never bytes / exceptions). */
enum class ModelProblem { SOURCE_NOT_CONFIGURED, NEEDS_WIFI, NOT_ENOUGH_SPACE, DOWNLOAD_FAILED, VERIFY_FAILED, DELETE_FAILED }

sealed interface StartResult {
    data object Started : StartResult
    data object AlreadyRunning : StartResult
    data object AlreadyReady : StartResult
    data class Refused(val problem: ModelProblem) : StartResult
}

/** Opens [url] at byte [offset]. [FetchResponse.startOffset] = where the stream really starts (0 if Range was ignored). */
fun interface ModelFetcher {
    fun open(url: String, offset: Long): FetchResponse
}

class FetchResponse(val stream: InputStream, val startOffset: Long)

/** True only on Wi-Fi / unmetered: a ~1 GB+ download never starts on mobile data without consent (MVP: Wi-Fi only). */
fun interface NetworkCheck {
    fun isUnmetered(): Boolean
}

/**
 * Downloads, verifies and deletes the 관련된 생각 model bundle in app-private, non-backed-up storage ([root] =
 * `noBackupFilesDir/models`, never next to Room / DataStore).
 *
 * Layout: `<root>/<id>/<version>/<fileName>` + `<fileName>.part` (download in progress) + `<fileName>.verified`
 * (marker = version + size + sha256 of the manifest the file was verified against).
 * Download: `.part` (resumed from its length, Range) → size check → sha256 → atomic rename to the final name → marker.
 * Ready needs final file + expected size + a marker equal to the current manifest. A final file without a matching
 * marker (crash / failed marker write after the rename) is never Ready: the next [refresh] re-hashes it and writes the
 * marker if it matches, otherwise deletes it. A failed / cancelled / corrupt download never becomes the final file.
 * One download at a time.
 * In-app (coroutine in [scope]): it continues while the process lives; after a process death the `.part` stays and the
 * next 모델 받기 resumes it (no WorkManager / DownloadManager — see docs/m6-related-design.md M6-6 note).
 *
 * [fetcher] null = this build has no network download implementation (no INTERNET permission yet).
 */
class ModelInstaller(
    private val root: File,
    private val manifest: List<ModelArtifact>,
    private val fetcher: ModelFetcher?,
    private val network: NetworkCheck,
    /** Free bytes where [root] lives (StatFs in the app). */
    private val freeBytes: () -> Long,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<ModelInstallState>(ModelInstallState.NotInstalled)
    val state: StateFlow<ModelInstallState> = _state.asStateFlow()

    /** Last refusal / failure (cleared when a new action starts). */
    private val _problem = MutableStateFlow<ModelProblem?>(null)
    val problem: StateFlow<ModelProblem?> = _problem.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    /**
     * Whether 모델 받기 can work at all in this build (every artifact fixed + a network implementation). False while the
     * production manifest is incomplete — Settings then shows the button disabled; it turns on by itself once set.
     */
    val downloadAvailable: Boolean get() = fetcher != null && manifest.isNotEmpty() && manifest.all { it.isConfigured }

    /** Sum of the manifest sizes; null while any artifact is not configured. */
    val totalBytes: Long? get() = if (manifest.all { it.expectedBytes != null }) manifest.sumOf { it.expectedBytes!! } else null

    /** Known part of the size (for "약 n GB 이상" while something is unconfigured). */
    val knownBytes: Long get() = manifest.sumOf { it.expectedBytes ?: 0L }

    private fun dir(a: ModelArtifact) = File(File(root, a.id), a.version)
    private fun finalFile(a: ModelArtifact) = File(dir(a), a.fileName!!)
    private fun partFile(a: ModelArtifact) = File(dir(a), a.fileName!! + ".part")
    private fun markerFile(a: ModelArtifact) = File(dir(a), a.fileName!! + ".verified")
    private fun marker(a: ModelArtifact) = "${a.version}\n${a.expectedBytes}\n${a.sha256}"

    private fun isVerified(a: ModelArtifact): Boolean {
        if (!a.isConfigured) return false
        val f = finalFile(a)
        val m = markerFile(a)
        return f.isFile && f.length() == a.expectedBytes && m.isFile && runCatching { m.readText() }.getOrNull() == marker(a)
    }

    /**
     * Re-read the disk (app start / Settings open). Also the crash-recovery step: removes other versions and stray
     * files, drops `.part` files bigger than expected; a valid `.part` of the current version stays for resume.
     * Never touches a running download.
     */
    suspend fun refresh() = withContext(io) {
        synchronized(lock) { if (job?.isActive == true) return@withContext }
        cleanup()
        _state.value = currentDiskState()
    }

    private fun currentDiskState(): ModelInstallState =
        if (manifest.isNotEmpty() && manifest.all(::isVerified)) ModelInstallState.Ready(manifest.sumOf { it.expectedBytes!! })
        else ModelInstallState.NotInstalled

    private fun cleanup() {
        val wanted = manifest.associateBy { it.id }
        root.listFiles()?.forEach { idDir ->
            val a = wanted[idDir.name]
            if (a == null) { idDir.deleteRecursively(); return@forEach }
            idDir.listFiles()?.forEach { verDir -> if (verDir.name != a.version) verDir.deleteRecursively() }
            if (a.fileName == null) return@forEach
            val keep = setOf(a.fileName, a.fileName + ".part", a.fileName + ".verified") // stray .verified.tmp is dropped
            dir(a).listFiles()?.forEach { if (it.name !in keep) it.deleteRecursively() }
            val part = partFile(a)
            if (part.isFile && a.expectedBytes != null && part.length() > a.expectedBytes) part.delete()
            // final file without a matching marker (crash / failed marker write after rename, or an older manifest):
            // re-verify once; only an exact size + sha256 match gets its marker, anything else is deleted
            if (finalFile(a).exists() && !isVerified(a)) {
                val f = finalFile(a)
                if (a.isConfigured && f.length() == a.expectedBytes && sha256(f) == a.sha256 && writeMarker(a)) Unit
                else { f.delete(); markerFile(a).delete() }
            }
        }
    }

    /** Checks, then downloads in [scope]. Never starts twice. */
    fun startDownload(): StartResult {
        synchronized(lock) {
            if (job?.isActive == true) return StartResult.AlreadyRunning
            val checked = preflight()
            if (checked != StartResult.Started) return checked
            _problem.value = null
            job = scope.launch { runDownload() }
            return StartResult.Started
        }
    }

    private fun preflight(): StartResult {
        if (manifest.isNotEmpty() && manifest.all(::isVerified)) return StartResult.AlreadyReady
        if (fetcher == null || manifest.any { !it.isConfigured }) return refuse(ModelProblem.SOURCE_NOT_CONFIGURED)
        if (!network.isUnmetered()) return refuse(ModelProblem.NEEDS_WIFI)
        if (freeBytes() < requiredFreeBytes()) return refuse(ModelProblem.NOT_ENOUGH_SPACE)
        return StartResult.Started
    }

    private fun refuse(p: ModelProblem): StartResult {
        _problem.value = p
        return StartResult.Refused(p)
    }

    /**
     * Bytes still to write (what each `.part` lacks; verified files count 0) + a safety margin. `.part` is renamed in
     * place (no copy), so the peak is the bundle size itself.
     */
    fun requiredFreeBytes(): Long {
        val remaining = manifest.sumOf { a ->
            val total = a.expectedBytes ?: 0L
            when {
                isVerified(a) -> 0L
                a.fileName != null && partFile(a).isFile && partFile(a).length() <= total -> total - partFile(a).length()
                else -> total
            }
        }
        val margin = maxOf(MIN_MARGIN_BYTES, (totalBytes ?: knownBytes) / 10)
        return remaining + margin
    }

    /** Waits for a running download (tests, delete). */
    suspend fun awaitDownload() { synchronized(lock) { job }?.join() }

    private suspend fun runDownload() {
        val total = manifest.sumOf { it.expectedBytes!! }
        var done = manifest.filter(::isVerified).sumOf { it.expectedBytes!! }
        _state.value = ModelInstallState.Downloading(done, total)
        try {
            for (a in manifest) {
                if (isVerified(a)) continue
                val ok = withContext(io) {
                    downloadOne(a) { bytes -> _state.value = ModelInstallState.Downloading(done + bytes, total) }
                }
                if (ok != null) {
                    _problem.value = ok
                    _state.value = currentDiskState()
                    return
                }
                done += a.expectedBytes!!
            }
            _state.value = currentDiskState()
        } catch (e: CancellationException) {
            _state.value = currentDiskState()
            throw e
        }
    }

    /** Returns null on success, or the problem. Never leaves an unverified final file. */
    private suspend fun downloadOne(a: ModelArtifact, progress: (Long) -> Unit): ModelProblem? {
        val expected = a.expectedBytes!!
        val url = (a.source as ModelSource.Https).url
        dir(a).mkdirs()
        val part = partFile(a)
        if (part.isFile && part.length() > expected) part.delete()
        val offset = if (part.isFile) part.length() else 0L
        try {
            val response = fetcher!!.open(url, offset)
            val append = response.startOffset == offset && offset > 0
            response.stream.use { input ->
                FileOutputStream(part, append).use { out ->
                    var written = if (append) offset else 0L
                    progress(written)
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        if (written + n > expected) { out.close(); part.delete(); return ModelProblem.VERIFY_FAILED }
                        out.write(buf, 0, n)
                        written += n
                        progress(written)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e // the .part stays for resume
        } catch (e: Exception) {
            return ModelProblem.DOWNLOAD_FAILED // network / IO: the .part stays for resume
        }
        if (part.length() != expected) return ModelProblem.DOWNLOAD_FAILED // ended early: resume next time
        if (sha256(part) != a.sha256) { part.delete(); return ModelProblem.VERIFY_FAILED }
        val final = finalFile(a)
        markerFile(a).delete() // no stale marker may vouch for the new file
        if (final.exists()) final.delete()
        if (!part.renameTo(final)) return ModelProblem.DOWNLOAD_FAILED // same dir: atomic; the verified .part stays
        // Not Ready until the marker exists; if this fails, refresh() re-verifies the final file later.
        return if (writeMarker(a)) null else ModelProblem.DOWNLOAD_FAILED
    }

    /** Marker via temp file + rename, so a half-written marker never exists. */
    private fun writeMarker(a: ModelArtifact): Boolean = runCatching {
        val marker = markerFile(a)
        val tmp = File(marker.path + ".tmp")
        tmp.writeText(marker(a))
        tmp.renameTo(marker) || run { tmp.delete(); false }
    }.getOrDefault(false)

    /**
     * AI 모델 삭제: cancels a running download and deletes ONLY the model files ([root]). Records, related results and
     * embeddings in Room are untouched. Returns false (state re-read, problem set) if something could not be deleted.
     */
    suspend fun deleteAll(): Boolean {
        val running = synchronized(lock) { job }
        running?.cancel()
        running?.join()
        val ok = withContext(io) { !root.exists() || root.deleteRecursively() }
        _problem.value = if (ok) null else ModelProblem.DELETE_FAILED
        _state.value = withContext(io) { currentDiskState() }
        return ok
    }

    companion object {
        const val MIN_MARGIN_BYTES = 200L * 1024 * 1024
        private const val BUFFER = 64 * 1024

        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
