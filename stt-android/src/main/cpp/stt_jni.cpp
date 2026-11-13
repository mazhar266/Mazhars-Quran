// JNI bridge between com.hikmah.quran.stt.WhisperModel and whisper.cpp (v1.9.5).
// Transcribes one short utterance (16 kHz mono float PCM) at a time.

#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <string>

#include "whisper.h"

#define TAG "QuranStt"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

std::string toString(JNIEnv *env, jbyteArray bytes) {
    const jsize length = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(length), '\0');
    if (length > 0) env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(out.data()));
    return out;
}

jbyteArray toBytes(JNIEnv *env, const std::string &text) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out != nullptr && !text.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte *>(text.data()));
    }
    return out;
}

void logCallback(ggml_log_level level, const char *text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
}

std::mutex gMutex; // a whisper context handles one transcription at a time

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_hikmah_quran_stt_WhisperModel_nativeLoad(JNIEnv *env, jclass, jbyteArray path) {
    static std::once_flag once;
    std::call_once(once, [] { whisper_log_set(logCallback, nullptr); });

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context *context = whisper_init_from_file_with_params(toString(env, path).c_str(), params);
    if (context == nullptr) LOGE("speech model could not be loaded");
    return reinterpret_cast<jlong>(context);
}

// Returns the transcript as UTF-8, or an empty array when nothing was said.
JNIEXPORT jbyteArray JNICALL
Java_com_hikmah_quran_stt_WhisperModel_nativeTranscribe(JNIEnv *env, jclass, jlong handle, jfloatArray samples,
                                                         jint threads, jbyteArray language, jbyteArray hint) {
    auto *context = reinterpret_cast<whisper_context *>(handle);
    if (context == nullptr) return toBytes(env, "");

    const jsize count = env->GetArrayLength(samples);
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    const std::string languageCode = toString(env, language);
    const std::string prompt = toString(env, hint);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = true;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.language = languageCode.c_str(); // "auto" detects the spoken language
    params.initial_prompt = prompt.empty() ? nullptr : prompt.c_str();
    // Whisper normally encodes a fixed 30 s window (1500 frames). Commands are a
    // few seconds long, so encode about half of that: roughly twice as fast.
    // Going lower (tight to the clip length) was faster still but made decoding
    // unstable: occasional garbled names and 20-30 s retries.
    const int frames = static_cast<int>(static_cast<double>(count) / WHISPER_SAMPLE_RATE * 50.0) + 128;
    if (frames < 768) params.audio_ctx = 768;

    std::string text;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        if (whisper_full(context, params, pcm, count) != 0) {
            LOGE("transcription failed");
        } else {
            const int segments = whisper_full_n_segments(context);
            for (int i = 0; i < segments; i++) {
                // Whisper invents text for silence; drop segments it itself rates as non-speech.
                if (whisper_full_get_segment_no_speech_prob(context, i) > 0.6f) continue;
                text += whisper_full_get_segment_text(context, i);
            }
        }
    }
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    return toBytes(env, text);
}

} // extern "C"
