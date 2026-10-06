package app.oneulmundeuk

import app.oneulmundeuk.data.settings.RelatedThoughtsStatus
import app.oneulmundeuk.data.settings.relatedThoughtsStatus
import app.oneulmundeuk.related.model.FetchResponse
import app.oneulmundeuk.related.model.ModelArtifact
import app.oneulmundeuk.related.model.ModelFetcher
import app.oneulmundeuk.related.model.ModelInstallState
import app.oneulmundeuk.related.model.ModelInstaller
import app.oneulmundeuk.related.model.ModelProblem
import app.oneulmundeuk.related.model.ModelRole
import app.oneulmundeuk.related.model.ModelSource
import app.oneulmundeuk.related.model.RelatedModels
import app.oneulmundeuk.related.model.SemanticGate
import app.oneulmundeuk.related.model.StartResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch

/** Model bundle install / verify / delete — local byte sources only, no network. */
class ModelInstallerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() = scope.cancel()

    private val e5Bytes = ByteArray(150_000) { (it * 7 % 251).toByte() }
    private val qwenBytes = ByteArray(300_000) { (it * 13 % 241).toByte() }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun art(id: String, bytes: ByteArray, version: String = "v1", sha: String = sha(bytes)) = ModelArtifact(
        id = id, role = if (id == "e5") ModelRole.EMBEDDING else ModelRole.JUDGE, format = "bin", version = version,
        fileName = "$id.bin", expectedBytes = bytes.size.toLong(), sha256 = sha, source = ModelSource.Https("test://$id"),
    )

    /** Serves files by url, honouring the offset (Range) unless told not to; can fail or corrupt on demand. */
    private inner class FakeFetcher(
        val files: Map<String, ByteArray>,
        var ignoreRange: Boolean = false,
        var failAfter: Long? = null,
        var corrupt: Boolean = false,
    ) : ModelFetcher {
        val calls = mutableListOf<Pair<String, Long>>()
        override fun open(url: String, offset: Long): FetchResponse {
            calls += url to offset
            val data = files.getValue(url).copyOf().also { if (corrupt) it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
            val start = if (ignoreRange) 0L else offset
            val limit = failAfter
            val body = data.copyOfRange(start.toInt(), data.size)
            val stream: InputStream = if (limit == null) ByteArrayInputStream(body) else object : InputStream() {
                var pos = 0
                override fun read(): Int {
                    if (start + pos >= limit) throw IOException("connection lost")
                    return if (pos < body.size) body[pos++].toInt() and 0xff else -1
                }
            }
            return FetchResponse(stream, start)
        }
    }

    private val root get() = File(tmp.root, "models")

    private fun installer(
        manifest: List<ModelArtifact> = listOf(art("e5", e5Bytes), art("qwen", qwenBytes)),
        fetcher: ModelFetcher? = FakeFetcher(mapOf("test://e5" to e5Bytes, "test://qwen" to qwenBytes)),
        wifi: Boolean = true,
        free: Long = Long.MAX_VALUE,
    ) = ModelInstaller(root, manifest, fetcher, { wifi }, { free }, scope, Dispatchers.IO)

    private fun ModelInstaller.install(): ModelInstallState = runBlocking {
        assertEquals(StartResult.Started, startDownload())
        awaitDownload()
        state.value
    }

    private fun ModelInstaller.refreshed(): ModelInstallState = runBlocking { refresh(); state.value }

    @Test
    fun nothingOnDiskIsNotInstalled() {
        assertEquals(ModelInstallState.NotInstalled, installer().refreshed())
    }

    @Test
    fun bothVerifiedIsReady() {
        val i = installer()
        assertEquals(ModelInstallState.Ready((e5Bytes.size + qwenBytes.size).toLong()), i.install())
        assertEquals(StartResult.AlreadyReady, i.startDownload()) // no re-download of the same version
        assertEquals(ModelInstallState.Ready((e5Bytes.size + qwenBytes.size).toLong()), installer().refreshed()) // next app start
    }

    @Test
    fun onlyOneModelIsNotReady() {
        val fetcher = FakeFetcher(mapOf("test://e5" to e5Bytes, "test://qwen" to qwenBytes), failAfter = 1_000)
        val onlyE5 = FakeFetcher(mapOf("test://e5" to e5Bytes))
        installer(manifest = listOf(art("e5", e5Bytes)), fetcher = onlyE5).install() // e5 alone, same paths
        val i = installer(fetcher = fetcher)
        assertEquals(ModelInstallState.NotInstalled, i.refreshed()) // e5 verified, qwen missing
        i.install()
        assertEquals(ModelInstallState.NotInstalled, i.state.value)
        assertEquals(ModelProblem.DOWNLOAD_FAILED, i.problem.value)
    }

    @Test
    fun wrongSizeOrHashIsNotReady() {
        installer().install()
        File(root, "qwen/v1/qwen.bin").appendBytes(byteArrayOf(1)) // size changed after install
        assertEquals(ModelInstallState.NotInstalled, installer().refreshed())
        assertFalse(File(root, "qwen/v1/qwen.bin").exists()) // untrusted final file removed

        installer().install()
        val otherSha = listOf(art("e5", e5Bytes), art("qwen", qwenBytes, sha = "0".repeat(64))) // manifest hash differs
        assertEquals(ModelInstallState.NotInstalled, installer(manifest = otherSha).refreshed())
    }

    /** Order: verified .part → rename → marker. A final file without its marker is never Ready by itself. */
    @Test
    fun finalFileWithoutMarkerIsReverifiedOnRecovery() {
        installer().install()
        val marker = File(root, "qwen/v1/qwen.bin.verified")
        assertTrue(marker.readText().startsWith("v1\n")) // version · size · sha256
        marker.delete() // e.g. marker write failed / process died right after the rename

        val i = installer()
        assertTrue(i.refreshed() is ModelInstallState.Ready) // re-hashed, matched, marker rewritten
        assertTrue(marker.exists())
    }

    @Test
    fun corruptFinalFileWithoutMarkerIsDeletedOnRecovery() {
        installer().install()
        val final = File(root, "qwen/v1/qwen.bin")
        File(root, "qwen/v1/qwen.bin.verified").delete()
        final.writeBytes(final.readBytes().also { it[0] = (it[0] + 1).toByte() }) // same size, wrong content
        assertEquals(ModelInstallState.NotInstalled, installer().refreshed())
        assertFalse(final.exists())
    }

    @Test
    fun downloadAvailableOnlyWithCompleteManifestAndFetcher() {
        assertTrue(installer().downloadAvailable)
        assertFalse(installer(fetcher = null).downloadAvailable)
        assertFalse(installer(manifest = listOf(art("e5", e5Bytes).copy(source = ModelSource.Unconfigured))).downloadAvailable)
    }

    @Test
    fun newManifestVersionNeverReusesOldFiles() {
        installer().install()
        val v2 = listOf(art("e5", e5Bytes, version = "v2"), art("qwen", qwenBytes, version = "v2"))
        assertEquals(ModelInstallState.NotInstalled, installer(manifest = v2).refreshed())
        assertFalse(File(root, "qwen/v1").exists()) // old version cleaned up
    }

    @Test
    fun failedDownloadNeverBecomesFinalAndResumes() {
        val fetcher = FakeFetcher(mapOf("test://e5" to e5Bytes, "test://qwen" to qwenBytes), failAfter = 100_000)
        val i = installer(fetcher = fetcher)
        i.install()
        assertEquals(ModelProblem.DOWNLOAD_FAILED, i.problem.value)
        assertFalse(File(root, "e5/v1/e5.bin").exists())
        val part = File(root, "e5/v1/e5.bin.part")
        assertEquals(100_000L, part.length()) // kept for resume

        assertEquals(ModelInstallState.NotInstalled, installer().refreshed()) // app restart: a .part is not READY
        assertTrue(part.exists()) // ...and is kept

        fetcher.failAfter = null
        assertTrue(i.install() is ModelInstallState.Ready)
        assertTrue(fetcher.calls.contains("test://e5" to 100_000L)) // resumed from the .part (Range)
        assertFalse(part.exists())
    }

    @Test
    fun serverIgnoringRangeStillInstallsCorrectly() {
        val fetcher = FakeFetcher(mapOf("test://e5" to e5Bytes, "test://qwen" to qwenBytes), failAfter = 50_000)
        val i = installer(fetcher = fetcher)
        i.install()
        fetcher.failAfter = null
        fetcher.ignoreRange = true
        assertTrue(i.install() is ModelInstallState.Ready) // restarted from 0, verified
    }

    @Test
    fun corruptDownloadIsDiscarded() {
        val fetcher = FakeFetcher(mapOf("test://e5" to e5Bytes, "test://qwen" to qwenBytes), corrupt = true)
        val i = installer(fetcher = fetcher)
        assertEquals(ModelInstallState.NotInstalled, i.install())
        assertEquals(ModelProblem.VERIFY_FAILED, i.problem.value)
        assertFalse(File(root, "e5/v1/e5.bin").exists())
        assertFalse(File(root, "e5/v1/e5.bin.part").exists()) // a corrupt .part is not resumed
    }

    @Test
    fun oversizedPartIsDropped() {
        File(root, "e5/v1").mkdirs()
        File(root, "e5/v1/e5.bin.part").writeBytes(ByteArray(e5Bytes.size + 10))
        installer().refreshed()
        assertFalse(File(root, "e5/v1/e5.bin.part").exists())
    }

    @Test
    fun notEnoughSpaceRefusesBeforeAnyDownload() {
        val fetcher = FakeFetcher(mapOf("test://e5" to e5Bytes, "test://qwen" to qwenBytes))
        val i = installer(fetcher = fetcher, free = ModelInstaller.MIN_MARGIN_BYTES) // margin alone, not the files
        assertEquals(StartResult.Refused(ModelProblem.NOT_ENOUGH_SPACE), i.startDownload())
        assertTrue(fetcher.calls.isEmpty())
        assertEquals((e5Bytes.size + qwenBytes.size) + ModelInstaller.MIN_MARGIN_BYTES, i.requiredFreeBytes()) // from expectedBytes
    }

    @Test
    fun mobileDataRefuses() {
        val fetcher = FakeFetcher(mapOf("test://e5" to e5Bytes, "test://qwen" to qwenBytes))
        assertEquals(StartResult.Refused(ModelProblem.NEEDS_WIFI), installer(fetcher = fetcher, wifi = false).startDownload())
        assertTrue(fetcher.calls.isEmpty())
    }

    @Test
    fun duplicateStartIsRefused() {
        val gate = CountDownLatch(1)
        val slow = ModelFetcher { _, offset ->
            FetchResponse(object : InputStream() {
                val body = ByteArrayInputStream(e5Bytes)
                override fun read(): Int { gate.await(); return body.read() }
                override fun read(b: ByteArray, off: Int, len: Int): Int { gate.await(); return body.read(b, off, len) }
            }, offset)
        }
        val i = installer(manifest = listOf(art("e5", e5Bytes)), fetcher = slow)
        assertEquals(StartResult.Started, i.startDownload())
        assertEquals(StartResult.AlreadyRunning, i.startDownload())
        gate.countDown()
        runBlocking { i.awaitDownload() }
        assertTrue(i.state.value is ModelInstallState.Ready)
    }

    @Test
    fun deleteRemovesOnlyModelFiles() {
        val keep = File(tmp.root, "journal.db").apply { writeText("records") } // stands for app data next to models
        val i = installer()
        i.install()
        assertTrue(runBlocking { i.deleteAll() })
        assertFalse(root.exists())
        assertTrue(keep.exists())
        assertEquals(ModelInstallState.NotInstalled, i.state.value)
    }

    @Test
    fun productionManifestIsUnconfiguredAndRefusesSafely() {
        val i = ModelInstaller(root, RelatedModels.BUNDLE, fetcher = null, { true }, { Long.MAX_VALUE }, scope)
        assertFalse(i.downloadAvailable) // Settings: disabled button + "준비 중" line
        assertEquals(StartResult.Refused(ModelProblem.SOURCE_NOT_CONFIGURED), i.startDownload())
        assertEquals(ModelInstallState.NotInstalled, runBlocking { i.refresh(); i.state.value })
        assertEquals(null, i.totalBytes) // e5 Android artifact not fixed → no total, only a lower bound
        assertEquals(1_274_396_992L, i.knownBytes)
        // Qwen: exactly the PoC-verified GGUF
        assertEquals("qwen3.5-2b-q4_K_M.gguf", RelatedModels.QWEN.fileName)
        assertEquals("20cb277f0967ace47b0b5d5658e5e494a88937f7378b74b5a266496400938f4c", RelatedModels.QWEN.sha256)
        assertTrue(RelatedModels.BUNDLE.all { it.source == ModelSource.Unconfigured }) // no guessed host
    }

    @Test
    fun gateAndSettingsStatus() {
        val ready = ModelInstallState.Ready(1)
        assertTrue(SemanticGate.allows(relatedEnabled = true, install = ready))
        assertFalse(SemanticGate.allows(relatedEnabled = false, install = ready)) // OFF: models kept, no inference
        assertFalse(SemanticGate.allows(relatedEnabled = true, install = ModelInstallState.NotInstalled))
        assertFalse(SemanticGate.allows(relatedEnabled = true, install = ModelInstallState.Downloading(1, 2)))
        assertEquals(RelatedThoughtsStatus.OFF, relatedThoughtsStatus(enabled = false, modelInstalled = true))
        assertEquals(RelatedThoughtsStatus.DOWNLOADING, relatedThoughtsStatus(enabled = true, modelInstalled = false, downloading = true))
    }
}
