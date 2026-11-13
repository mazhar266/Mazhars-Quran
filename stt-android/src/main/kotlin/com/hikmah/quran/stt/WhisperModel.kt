package com.hikmah.quran.stt

import android.os.SystemClock
import android.util.Log
import java.io.File

/** whisper.cpp speech-to-text on the device's CPU. Audio is 16 kHz mono float PCM. */
class WhisperModel private constructor(private var handle: Long) {

    /** Blocking; call off the main thread. Returns null when no speech was recognised. */
    @Synchronized
    fun transcribe(samples: FloatArray, language: String = "auto"): String? {
        if (handle == 0L) return null // unloaded
        val start = SystemClock.elapsedRealtime()
        // Big cores only: on phones the efficiency cores slow inference down.
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
        val raw = nativeTranscribe(
            handle, samples, threads, language.encodeToByteArray(), VOCABULARY_HINT.encodeToByteArray(),
        ).decodeToString()
        val text = clean(raw)
        val seconds = samples.size / SAMPLE_RATE.toFloat()
        Log.i(TAG, "transcribed %.1f s of audio in %d ms: %s".format(seconds, SystemClock.elapsedRealtime() - start, text))
        return text
    }

    /** Frees the model's memory. Waits for a running transcription to finish first. */
    @Synchronized
    fun close() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val TAG = "QuranStt"

        /**
         * Given to Whisper as preceding context. It biases spelling towards
         * the names people actually say, which a general model otherwise
         * writes as unrelated English words.
         */
        private const val VOCABULARY_HINT =
            "Quran app. I recite Surah Yaseen, Surah Al-Mulk, Surah Al-Waqiah, Surah Ar-Rahman, " +
                "Surah Al-Kahf, Surah Al-Baqarah, Surah Al-Fatihah, Surah Al-Ikhlas and Surah Al-Falaq daily."

        fun load(model: File): WhisperModel? = runCatching {
            System.loadLibrary("quranstt")
            val start = SystemClock.elapsedRealtime()
            val handle = nativeLoad(model.absolutePath.encodeToByteArray())
            check(handle != 0L) { "whisper.cpp could not load ${model.name}" }
            Log.i(TAG, "loaded ${model.name} in ${SystemClock.elapsedRealtime() - start} ms")
            WhisperModel(handle)
        }.onFailure { Log.e(TAG, "speech model unavailable", it) }.getOrNull()

        /** Removes Whisper's non-speech annotations such as "[BLANK_AUDIO]" or "(music)". */
        internal fun clean(raw: String): String? =
            raw.replace(Regex("\\[[^\\]]*\\]|\\([^)]*\\)|\\*[^*]*\\*"), " ")
                .replace(Regex("\\s+"), " ").trim()
                .takeIf { text -> text.any { it.isLetterOrDigit() } }

        @JvmStatic private external fun nativeLoad(path: ByteArray): Long
        @JvmStatic private external fun nativeTranscribe(
            handle: Long, samples: FloatArray, threads: Int, language: ByteArray, hint: ByteArray,
        ): ByteArray
        @JvmStatic private external fun nativeFree(handle: Long)
    }
}
