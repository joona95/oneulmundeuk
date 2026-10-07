package app.oneulmundeuk.related.qwen

import android.os.Build
import java.io.File

/**
 * Qwen through the app's llama.cpp JNI wrapper (`app/src/main/cpp`, libqwen_judge.so, arm64-v8a only).
 * One native session (model + context + chat template) per instance; calls are serialised here and in the native
 * session; [close] frees everything once (also after a failed call). Every native error surfaces as an exception
 * from [load] / [complete] — never a crash of the caller's thread beyond that.
 */
class LlamaCppQwenEngine private constructor(private var handle: Long) : QwenEngine {
    private val lock = Any()

    override fun complete(system: String, user: String, jsonSchema: String): ChatCompletion = synchronized(lock) {
        check(handle != 0L) { "closed" }
        val out = QwenNative.nativeComplete(handle, system.toByteArray(), user.toByteArray(), jsonSchema.toByteArray())
        check(out.size == 2) { "bad native result" }
        val finish = when (String(out[1], Charsets.UTF_8)) {
            "stop" -> Finish.STOP
            "length" -> Finish.LENGTH
            "context" -> Finish.CONTEXT
            else -> error("unknown finish ${String(out[1], Charsets.UTF_8)}")
        }
        ChatCompletion(String(out[0], Charsets.UTF_8), finish)
    }

    override fun close() = synchronized(lock) {
        if (handle != 0L) {
            QwenNative.nativeFree(handle)
            handle = 0L
        }
    }

    companion object {
        /** Loads the GGUF (seconds, ~2.7 GB peak RSS in the PoC). Call off the main thread, only after [QwenRuntimeSupport.available]. */
        fun load(model: File): LlamaCppQwenEngine {
            check(QwenRuntimeSupport.available) { "llama.cpp runtime not available on this device" }
            val handle = QwenNative.nativeLoad(model.absolutePath.toByteArray(), QwenGeneration.N_CTX, QwenGeneration.N_PREDICT, QwenGeneration.SEED)
            check(handle != 0L) { "model load failed" }
            return LlamaCppQwenEngine(handle)
        }
    }
}

/** JNI entry points (app/src/main/cpp/qwen_judge_jni.cpp). Strings cross as UTF-8 bytes (JNI "modified UTF-8" breaks emoji). */
internal object QwenNative {
    @JvmStatic external fun nativeLoad(modelPath: ByteArray, nCtx: Int, nPredict: Int, seed: Int): Long

    /** → [content UTF-8, finish ("stop" | "length" | "context")]. Throws RuntimeException on a native error. */
    @JvmStatic external fun nativeComplete(handle: Long, system: ByteArray, user: ByteArray, jsonSchema: ByteArray): Array<ByteArray>

    @JvmStatic external fun nativeFree(handle: Long)
}

/**
 * Can this device run the native judge? arm64-v8a + the CPU features the kernels were compiled for
 * (`GGML_CPU_ARM_ARCH=armv8.6-a+dotprod+i8mm`, same as the PoC build) + the library loads. Otherwise only the semantic
 * feature is unavailable (never a SIGILL: the library is not even loaded on such a CPU).
 */
object QwenRuntimeSupport {
    const val LIBRARY = "qwen_judge"

    /** /proc/cpuinfo "Features" names that `-march=armv8.6-a+dotprod+i8mm` code may use. */
    val REQUIRED_CPU_FEATURES = setOf("asimddp", "i8mm", "bf16")

    fun cpuSupports(featuresLine: String): Boolean {
        val names = featuresLine.substringAfter(':', "").trim().split(Regex("\\s+")).toSet()
        return REQUIRED_CPU_FEATURES.all { it in names }
    }

    val available: Boolean by lazy {
        runCatching {
            Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a" &&
                cpuSupports(File("/proc/cpuinfo").useLines { lines -> lines.firstOrNull { it.startsWith("Features") }.orEmpty() }) &&
                run { System.loadLibrary(LIBRARY); true }
        }.getOrElse { false } // UnsatisfiedLinkError (other ABI / missing .so) etc. → unavailable
    }
}
