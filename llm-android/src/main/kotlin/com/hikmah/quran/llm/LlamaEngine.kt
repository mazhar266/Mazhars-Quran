package com.hikmah.quran.llm

import android.os.SystemClock
import android.util.Log
import com.hikmah.quran.voice.LlmCommand
import com.hikmah.quran.voice.LlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/** llama.cpp running a GGUF model on the device's CPU. */
class LlamaEngine private constructor(private var handle: Long) : LlmEngine {

    override suspend fun complete(system: String, user: String, grammar: String): String =
        withContext(inference) {
            check(handle != 0L) { "The model has been unloaded" }
            val start = SystemClock.elapsedRealtime()
            val output = nativeComplete(
                handle, system.encodeToByteArray(), user.encodeToByteArray(), grammar.encodeToByteArray(), MAX_TOKENS,
            ).decodeToString()
            Log.i(TAG, "completion took ${SystemClock.elapsedRealtime() - start} ms: $output")
            output.ifEmpty { error("The model produced no output") }
        }

    /**
     * Evaluates the system prompt once so that real commands only have to
     * process the user's sentence. Takes a while on slow devices.
     */
    suspend fun warmUp() {
        val start = SystemClock.elapsedRealtime()
        complete(LlmCommand.SYSTEM_PROMPT, "hello", LlmCommand.GRAMMAR)
        Log.i(TAG, "warmed up in ${SystemClock.elapsedRealtime() - start} ms")
    }

    /** Frees the model's memory. Waits for a running completion to finish first. */
    suspend fun close() = withContext(inference + NonCancellable) {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0
            Log.i(TAG, "unloaded")
        }
    }

    companion object {
        private const val TAG = "QuranLlm"
        private const val MAX_TOKENS = 96
        private const val CONTEXT_TOKENS = 2048

        /** llama.cpp contexts are not thread-safe: one native call at a time, process-wide. */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val inference = Dispatchers.Default.limitedParallelism(1)

        /** Loads [model] into memory, or returns null if llama.cpp cannot read it. */
        suspend fun load(model: File): LlamaEngine? = withContext(inference) {
            runCatching {
                System.loadLibrary("quranllm")
                val start = SystemClock.elapsedRealtime()
                // Big cores only: on phones the efficiency cores slow llama.cpp down.
                val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
                val handle = nativeLoad(model.absolutePath.encodeToByteArray(), threads, CONTEXT_TOKENS)
                check(handle != 0L) { "llama.cpp could not load ${model.name}" }
                Log.i(TAG, "loaded ${model.name} in ${SystemClock.elapsedRealtime() - start} ms, $threads threads")
                LlamaEngine(handle)
            }.onFailure { Log.e(TAG, "model unavailable", it) }.getOrNull()
        }

        @JvmStatic private external fun nativeLoad(path: ByteArray, threads: Int, contextSize: Int): Long
        @JvmStatic private external fun nativeComplete(
            handle: Long, system: ByteArray, user: ByteArray, grammar: ByteArray, maxTokens: Int,
        ): ByteArray
        @JvmStatic private external fun nativeFree(handle: Long)
    }
}
