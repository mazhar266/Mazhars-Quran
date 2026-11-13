package com.hikmah.quran.stt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.hikmah.quran.voice.VoiceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

/**
 * Tap-to-talk voice input: records from the microphone between [start] and
 * [finish], then transcribes on-device with [WhisperModel]. Nothing is
 * recorded outside that window and no audio leaves the device or is stored.
 *
 * [requestPermission] asks the user for microphone access and returns the answer.
 */
class MicrophoneVoiceInput(
    context: Context,
    private val requestPermission: suspend () -> Boolean,
) : VoiceInput {
    private val context = context.applicationContext
    private var recording: Job? = null
    private val chunks = ArrayList<FloatArray>()

    override suspend fun start(): Boolean {
        if (recording != null) return true
        if (model(context).await() == null) return false
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted && !requestPermission()) return false

        val recorder = createRecorder() ?: return false
        synchronized(chunks) { chunks.clear() }
        recording = scope.launch(Dispatchers.IO) {
            val buffer = FloatArray(WhisperModel.SAMPLE_RATE / 10)
            var total = 0
            try {
                recorder.startRecording()
                while (isActive && total < MAX_SAMPLES) {
                    val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) break
                    synchronized(chunks) { chunks += buffer.copyOf(read) }
                    total += read
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
        return true
    }

    override suspend fun finish(): String? {
        val job = recording ?: return null
        recording = null
        job.cancel()
        job.join()
        val samples = synchronized(chunks) {
            val all = FloatArray(chunks.sumOf { it.size })
            var offset = 0
            for (chunk in chunks) { chunk.copyInto(all, offset); offset += chunk.size }
            chunks.clear()
            all
        }
        if (samples.size < WhisperModel.SAMPLE_RATE / 2) return null // under half a second
        val loudness = sqrt(samples.fold(0.0) { sum, s -> sum + s * s } / samples.size)
        Log.i(TAG, "recorded %.1f s, loudness %.4f".format(samples.size / WhisperModel.SAMPLE_RATE.toFloat(), loudness))
        if (loudness < SILENCE_LOUDNESS) return null // Whisper invents text for silence
        val whisper = model(context).await() ?: return null
        return withContext(Dispatchers.Default) { whisper.transcribe(samples) }
    }

    override fun cancel() {
        recording?.cancel()
        recording = null
    }

    @SuppressLint("MissingPermission") // checked in start()
    private fun createRecorder(): AudioRecord? = runCatching {
        val minimum = AudioRecord.getMinBufferSize(
            WhisperModel.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            WhisperModel.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minimum, WhisperModel.SAMPLE_RATE * Float.SIZE_BYTES), // one second
        ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }.onFailure { Log.e(TAG, "microphone unavailable", it) }.getOrNull()

    companion object {
        private const val TAG = "QuranStt"
        private const val MAX_SAMPLES = WhisperModel.SAMPLE_RATE * 15
        private const val SILENCE_LOUDNESS = 0.003

        private val scope = CoroutineScope(SupervisorJob())
        private var loading: Deferred<WhisperModel?>? = null

        /** Where a speech model is looked for: any `ggml-*.bin` in the app's private `files/models`. */
        fun modelDirectory(context: Context): File = File(context.filesDir, "models")

        /** Loads the speech model once per process, in the background. */
        @Synchronized
        fun model(context: Context): Deferred<WhisperModel?> = loading ?: scope.async(Dispatchers.IO) {
            val file = modelDirectory(context)
                .listFiles { f -> f.name.startsWith("ggml-") && f.extension == "bin" }?.minOrNull()
            if (file == null) {
                Log.i(TAG, "no speech model in ${modelDirectory(context)}; voice input disabled")
                null
            } else {
                WhisperModel.load(file)
            }
        }.also { loading = it }
    }
}
