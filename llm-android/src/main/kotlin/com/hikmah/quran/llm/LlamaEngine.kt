package com.hikmah.quran.llm

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.hikmah.quran.voice.LlmCommand
import com.hikmah.quran.voice.LlmEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

/** llama.cpp running a GGUF model on the device's CPU. */
class LlamaEngine private constructor(private val handle: Long) : LlmEngine {

    override suspend fun complete(system: String, user: String, grammar: String): String =
        withContext(inference) {
            val start = SystemClock.elapsedRealtime()
            val output = nativeComplete(
                handle, system.encodeToByteArray(), user.encodeToByteArray(), grammar.encodeToByteArray(), MAX_TOKENS,
            ).decodeToString()
            Log.i(TAG, "completion took ${SystemClock.elapsedRealtime() - start} ms: $output")
            output.ifEmpty { error("The model produced no output") }
        }

    companion object {
        private const val TAG = "QuranLlm"
        private const val MAX_TOKENS = 96
        private const val CONTEXT_TOKENS = 2048

        /** llama.cpp contexts are not thread-safe: one inference at a time. */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val inference = Dispatchers.Default.limitedParallelism(1)

        /** Where a model file is looked for. Later the in-app downloader will put it here. */
        fun modelDirectory(context: Context): File = File(context.filesDir, "models")

        /** The model lives as long as the process: it must survive activity re-creation. */
        private val processScope = CoroutineScope(SupervisorJob())
        private var shared: LlmEngine? = null

        /**
         * Starts loading the first `.gguf` in [modelDirectory] in the background
         * (once per process) and primes it with the system prompt, so the first
         * real command only has to evaluate the user's sentence. Commands sent
         * before it is ready, or when there is no model, fail fast and fall back
         * to the keyword rules.
         */
        @Synchronized
        fun shared(context: Context): LlmEngine =
            shared ?: loadInBackground(context.applicationContext).also { shared = it }

        private fun loadInBackground(context: Context): LlmEngine {
            val loading: Deferred<LlamaEngine?> = processScope.async(inference) {
                val model = modelDirectory(context).listFiles { file -> file.extension == "gguf" }?.minOrNull()
                if (model == null) {
                    Log.i(TAG, "no model in ${modelDirectory(context)}; using keyword rules only")
                    return@async null
                }
                runCatching {
                    System.loadLibrary("quranllm")
                    val start = SystemClock.elapsedRealtime()
                    // Big cores only: on phones the efficiency cores slow llama.cpp down.
                    val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
                    val handle = nativeLoad(model.absolutePath.encodeToByteArray(), threads, CONTEXT_TOKENS)
                    check(handle != 0L) { "llama.cpp could not load ${model.name}" }
                    val engine = LlamaEngine(handle)
                    Log.i(TAG, "loaded ${model.name} in ${SystemClock.elapsedRealtime() - start} ms, $threads threads")
                    engine.complete(LlmCommand.SYSTEM_PROMPT, "hello", LlmCommand.GRAMMAR)
                    Log.i(TAG, "ready after ${SystemClock.elapsedRealtime() - start} ms")
                    engine
                }.onFailure { Log.e(TAG, "model unavailable", it) }.getOrNull()
            }
            return object : LlmEngine {
                @OptIn(ExperimentalCoroutinesApi::class)
                override suspend fun complete(system: String, user: String, grammar: String): String {
                    check(loading.isCompleted) { "The model is still loading" }
                    val engine = loading.getCompleted() ?: error("No model installed")
                    return engine.complete(system, user, grammar)
                }
            }
        }

        @JvmStatic private external fun nativeLoad(path: ByteArray, threads: Int, contextSize: Int): Long
        @JvmStatic private external fun nativeComplete(
            handle: Long, system: ByteArray, user: ByteArray, grammar: ByteArray, maxTokens: Int,
        ): ByteArray
    }
}
