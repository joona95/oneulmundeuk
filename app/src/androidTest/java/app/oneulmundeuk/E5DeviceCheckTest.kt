package app.oneulmundeuk

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.oneulmundeuk.related.e5.E5Embedder
import app.oneulmundeuk.related.model.RelatedModels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * M6-8 device check of the e5 artifact (experiments/e5-android). Development only: the model / tokenizer / fixture are
 * copied into the debug app's INTERNAL files dir (never packaged), so this test SKIPS when they are absent:
 *
 *   adb push <files> /data/local/tmp/e5/
 *   adb shell run-as app.oneulmundeuk.debug sh -c 'mkdir -p files/e5 && cp -r /data/local/tmp/e5/. files/e5/'
 *
 * Not the external files dir: files that `adb shell` creates under /sdcard/Android/data/<pkg>/ are owned by the shell
 * user, and the app process (which also runs this instrumentation) may not be allowed to read them — File.isFile() is
 * then simply false. `run-as` copies as the app's own uid, so filesDir is always readable here.
 *
 * Checks tokenizer ids and embeddings against the host reference, related > unrelated pairs, then measures cold load,
 * RSS and warm latency (median). Writes files/e5/device_report-<model>.txt and logs tag "E5Device".
 *
 * Default = the production files (RelatedModels.E5 INT8-embrows + E5_TOKENIZER) with the INT8 tolerances. The FP32
 * reference export: -e e5model e5-small-ko-v2-fcfc26bf.onnx -e maxabs 0.001. Tolerances are vs the FP32 host reference.
 */
@RunWith(AndroidJUnit4::class)
class E5DeviceCheckTest {
    private val report = StringBuilder()

    private fun log(line: String) {
        Log.i("E5Device", line)
        report.appendLine(line)
    }

    private fun rssKb(): Long = File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }
        .split(Regex("\\s+"))[1].toLong()

    private fun b64(s: String) = String(Base64.getDecoder().decode(s), Charsets.UTF_8)

    private fun dot(a: FloatArray, b: FloatArray): Double { var s = 0.0; for (i in a.indices) s += a[i].toDouble() * b[i]; return s }

    private fun median(xs: List<Double>) = xs.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    @Test
    fun e5ArtifactOnDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val dir = File(ctx.filesDir, "e5")
        val model = File(dir, args.getString("e5model") ?: RelatedModels.E5.fileName!!)
        // INT8-embrows on host: max|diff| 1.5e-3, cosine 0.99996 (FP32: 4.6e-7) → 3e-3 / 0.9999 leaves ARM-rounding room
        val tolMaxAbs = args.getString("maxabs")?.toDouble() ?: 3e-3
        val tolMinCos = args.getString("mincos")?.toDouble() ?: 0.9999
        val tokenizerFile = File(dir, RelatedModels.E5_TOKENIZER.fileName!!)
        val fixture = File(dir, "device_fixture.tsv")
        val found = listOf(model, tokenizerFile, fixture).joinToString { "${it.name}: exists=${it.exists()} read=${it.canRead()}" }
        assumeTrue("e5 files not readable in $dir (dir exists=${dir.exists()}) — $found — skipped", model.isFile && tokenizerFile.isFile && fixture.isFile)
        log("device ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}) · ABI ${android.os.Build.SUPPORTED_ABIS.first()}")
        log("model ${model.name} ${model.length()} bytes · tokenizer ${tokenizerFile.length()} bytes · tolerance max|diff| ≤ $tolMaxAbs, cosine ≥ $tolMinCos")
        // SME / SME2 CPU features (KleidiAI SIGILL diagnosis, ORT issue #26377)
        val features = File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") }.orEmpty().split(Regex("\\s+"))
        log("cpu features: sme=${"sme" in features} sme2=${"sme2" in features} (${features.filter { it.startsWith("sme") }.joinToString(" ")})")

        // ── cold load ──
        System.gc()
        val rss0 = rssKb()
        val t0 = SystemClock.elapsedRealtimeNanos()
        val e5 = E5Embedder.load(model, tokenizerFile)
        val loadMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
        val rss1 = rssKb()
        log("cold load (tokenizer + ORT session) ${"%.0f".format(loadMs)} ms · VmRSS ${rss0 / 1024} → ${rss1 / 1024} MB (+${(rss1 - rss0) / 1024} MB) · native heap ${Debug.getNativeHeapAllocatedSize() / 1048576} MB")

        e5.use {
            // ── parity against the host reference ──
            var tokOk = 0
            var tokBad = 0
            var maxAbs = 0.0
            var minCos = 1.0
            var minNorm = 2.0
            var maxNorm = 0.0
            var finite = true
            var items = 0
            val pairs = ArrayList<List<String>>()
            val byLen = ArrayList<Pair<Int, String>>()
            fixture.forEachLine(Charsets.UTF_8) { line ->
                val f = line.split('\t')
                if (f[0] == "pair") { pairs += f; return@forEachLine }
                items++
                val text = b64(f[2])
                val want = f[3].split(',').map { it.toInt() }
                val ids = e5.tokenize(text)
                if (ids.toList() == want) tokOk++ else { tokBad++; log("TOKEN MISMATCH ${f[1]}") }
                byLen += want.size to text
                val ref = f[4].split(',').map { it.toFloat() }.toFloatArray()
                val got = runBlocking { e5.embedWithPrefix("", text) } // full path: tokenizer + ONNX
                assertEquals(E5Embedder.DIM, got.size)
                if (got.any { it.isNaN() || it.isInfinite() }) finite = false
                val n = sqrt(dot(got, got))
                minNorm = minOf(minNorm, n); maxNorm = maxOf(maxNorm, n)
                minCos = minOf(minCos, dot(got, ref) / n / sqrt(dot(ref, ref)))
                for (i in got.indices) maxAbs = maxOf(maxAbs, abs((got[i] - ref[i]).toDouble()))
            }
            log("tokenizer parity $tokOk / ${tokOk + tokBad}")
            log("embedding parity $items texts · dim ${E5Embedder.DIM} · finite $finite · norm ${"%.7f".format(minNorm)}–${"%.7f".format(maxNorm)} · max|diff| ${"%.2e".format(maxAbs)} · min cosine ${"%.8f".format(minCos)}")

            var pairsOk = 0
            for (p in pairs) {
                val (a, b, c) = runBlocking { listOf(e5.embedWithPrefix("", b64(p[2])), e5.embedWithPrefix("", b64(p[3])), e5.embedWithPrefix("", b64(p[4]))) }
                val sim = dot(a, b)
                val unrelated = dot(a, c)
                if (sim > unrelated) pairsOk++
                log("pair ${p[1]}: similar ${"%.4f".format(sim)} (host ${"%.4f".format(p[5].toDouble())}) · unrelated ${"%.4f".format(unrelated)} (host ${"%.4f".format(p[6].toDouble())})")
            }

            // ── warm latency (median of 15 after 3 warm-ups), full path and model only ──
            val sorted = byLen.sortedBy { it.first }
            val cases = listOf(
                "short" to sorted.first { it.first >= 8 },
                "typical" to sorted.minByOrNull { abs(it.first - 48) }!!,
                "long" to sorted.last(),
            )
            for ((name, case) in cases) {
                val ids = e5.tokenize(case.second)
                repeat(3) { runBlocking { e5.embedWithPrefix("", case.second) } }
                val full = (1..15).map { val s = SystemClock.elapsedRealtimeNanos(); runBlocking { e5.embedWithPrefix("", case.second) }; (SystemClock.elapsedRealtimeNanos() - s) / 1e6 }
                val modelOnly = (1..15).map { val s = SystemClock.elapsedRealtimeNanos(); e5.embedIds(ids); (SystemClock.elapsedRealtimeNanos() - s) / 1e6 }
                log("latency $name (${case.first} tokens): full ${"%.1f".format(median(full))} ms · model only ${"%.1f".format(median(modelOnly))} ms")
            }
            log("VmRSS after inference ${rssKb() / 1024} MB")
            File(dir, "device_report-${model.nameWithoutExtension}.txt").writeText(report.toString())

            assertEquals("tokenizer ids differ from the host reference", 0, tokBad)
            assertTrue("NaN / Inf in embeddings", finite)
            assertTrue("norm not ≈ 1", abs(minNorm - 1) < 1e-3 && abs(maxNorm - 1) < 1e-3)
            assertTrue("cosine vs host $minCos", minCos >= tolMinCos)
            assertTrue("max |diff| vs host $maxAbs", maxAbs <= tolMaxAbs)
            assertEquals("related pair not above unrelated", pairs.size, pairsOk)
        }
    }
}
