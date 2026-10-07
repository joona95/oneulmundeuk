package app.oneulmundeuk

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.oneulmundeuk.related.JudgeResult
import app.oneulmundeuk.related.model.RelatedModels
import app.oneulmundeuk.related.qwen.Finish
import app.oneulmundeuk.related.qwen.JudgeV1
import app.oneulmundeuk.related.qwen.LlamaCppQwenEngine
import app.oneulmundeuk.related.qwen.QwenJudge
import app.oneulmundeuk.related.qwen.QwenRuntimeSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

/**
 * M6-7 device check of the native judge (libqwen_judge.so) against the Android PoC (llama-server b10456, same GGUF,
 * same judge_v1 request). Development only: the GGUF and the fixture are copied into the debug app's INTERNAL files
 * dir (never packaged), so this test SKIPS when they are absent:
 *
 *   python3 experiments/android-qwen-poc/make_device_fixture.py
 *   adb push <qwen3.5-2b-q4_K_M.gguf> experiments/android-qwen-poc/out/qwen_device_fixture.tsv /data/local/tmp/qwen/
 *   adb shell run-as app.oneulmundeuk.debug sh -c 'mkdir -p files/qwen && cp -r /data/local/tmp/qwen/. files/qwen/'
 *
 * Runs the first N pairs (`-e qwenpairs N`, default 30; `all` = 157) in the PoC session order and reports load time,
 * RSS, per-pair latency and agreement with the PoC Android labels / raw outputs. Asserts only that every pair
 * produced a valid judge_v1 output (agreement is reported for review: the PoC is the reference, not an oracle).
 * Writes files/qwen/device_report.txt, logs tag "QwenDevice".
 */
@RunWith(AndroidJUnit4::class)
class QwenDeviceCheckTest {
    private val report = StringBuilder()

    private fun log(line: String) {
        Log.i("QwenDevice", line)
        report.appendLine(line)
    }

    private fun rssMb(key: String = "VmRSS"): Long =
        File("/proc/self/status").readLines().first { it.startsWith("$key:") }.split(Regex("\\s+"))[1].toLong() / 1024

    private fun b64(s: String) = String(Base64.getDecoder().decode(s), Charsets.UTF_8)

    @Test
    fun judgeV1OnDeviceMatchesThePoc() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val dir = File(ctx.filesDir, "qwen")
        val model = File(dir, RelatedModels.QWEN.fileName!!)
        val fixture = File(dir, "qwen_device_fixture.tsv")
        assumeTrue("qwen files not in $dir — skipped", model.isFile && fixture.isFile)
        assumeTrue("native judge not available on this device (ABI / CPU features) — skipped", QwenRuntimeSupport.available)
        log("device ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · ${QwenJudge.MODEL_ID}")
        log("model ${model.length()} bytes (expected ${RelatedModels.QWEN.expectedBytes})")

        val rows = fixture.readLines().filter { it.isNotBlank() }.map { it.split('\t') }
        val limit = args.getString("qwenpairs")?.let { if (it == "all") rows.size else it.toInt() } ?: 30
        val prompt = JudgeV1.parse(ctx.assets.open(JudgeV1.ASSET).use { it.readBytes() })

        System.gc()
        val rss0 = rssMb()
        val t0 = SystemClock.elapsedRealtime()
        val engine = LlamaCppQwenEngine.load(model)
        log("load ${SystemClock.elapsedRealtime() - t0} ms · VmRSS $rss0 → ${rssMb()} MB · native heap ${Debug.getNativeHeapAllocatedSize() / 1048576} MB")

        var valid = 0
        var sameLabel = 0
        var sameRaw = 0
        val latencies = ArrayList<Long>()
        engine.use {
            for (row in rows.take(limit)) {
                val (id, current, past, pocLabel, pocRaw) = row
                val s = SystemClock.elapsedRealtime()
                val out = engine.complete(prompt.system, prompt.user(b64(current), b64(past)), JudgeV1.SCHEMA_JSON)
                latencies += SystemClock.elapsedRealtime() - s
                val result = JudgeV1.validate(out.content, truncated = out.finish != Finish.STOP)
                val label = (result as? JudgeResult.Label)?.value
                if (label != null) valid++
                if (label?.toString() == pocLabel) sameLabel++
                if (out.content == b64(pocRaw)) sameRaw++
                log("$id label $label (PoC $pocLabel) ${latencies.last()} ms${if (out.content == b64(pocRaw)) " · raw =" else ""}")
            }
        }
        val sorted = latencies.sorted()
        log("pairs ${latencies.size} · valid $valid · same label as PoC $sameLabel · same raw output $sameRaw")
        log("latency median ${sorted[sorted.size / 2]} ms · p90 ${sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.size - 1)]} ms · max ${sorted.last()} ms · VmHWM ${rssMb("VmHWM")} MB")
        File(dir, "device_report.txt").writeText(report.toString())
        assertEquals("every pair gives a valid judge_v1 output", latencies.size, valid)
        assertTrue(latencies.isNotEmpty())
    }
}
