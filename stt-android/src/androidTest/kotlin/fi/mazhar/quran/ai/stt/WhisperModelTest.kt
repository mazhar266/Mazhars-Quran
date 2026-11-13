package fi.mazhar.quran.ai.stt

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transcribes WAV files on a device or emulator, to check the native speech
 * pipeline without a microphone. Skipped unless these exist in the test
 * app's private storage (copy them in with `run-as fi.mazhar.quran.ai.stt.test`):
 *
 *   files/stt/model.bin   a whisper.cpp ggml model
 *   files/stt/NAME.wav   16 kHz mono 16-bit PCM clips
 *
 * Each result is logged as `STT_RESULT NAME.wav => transcript`.
 */
@RunWith(AndroidJUnit4::class)
class WhisperModelTest {
    @Test
    fun transcribesWavFiles() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "stt")
        val modelFile = File(directory, "model.bin")
        val clips = directory.listFiles { file -> file.extension == "wav" }.orEmpty().sortedBy { it.name }
        assumeTrue("no model or clips in $directory", modelFile.exists() && clips.isNotEmpty())

        val model = WhisperModel.load(modelFile)
        assertNotNull(model)
        for (clip in clips) {
            val start = System.currentTimeMillis()
            val text = model!!.transcribe(readWav(clip), language = "en")
            Log.i("QuranSttTest", "STT_RESULT ${clip.name} (${System.currentTimeMillis() - start} ms) => $text")
        }
    }

    /** Minimal reader for 16-bit PCM WAV: finds the "data" chunk and converts to floats. */
    private fun readWav(file: File): FloatArray {
        val bytes = file.readBytes()
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (id == "data") {
                val length = minOf(size, bytes.size - offset - 8)
                val shorts = ByteBuffer.wrap(bytes, offset + 8, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                return FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
            }
            offset += 8 + size + (size and 1)
        }
        error("${file.name} has no data chunk")
    }
}
