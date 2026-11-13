package fi.mazhar.quran.ai.voice

/** Tap-to-talk speech input. Implemented per platform (microphone + on-device speech-to-text). */
interface VoiceInput {
    /** Starts listening. False if it cannot (no speech model, no microphone permission). */
    suspend fun start(): Boolean

    /** Stops listening and returns what was said, or null if nothing was recognised. */
    suspend fun finish(): String?

    /** Stops listening and discards the audio. */
    fun cancel()
}
