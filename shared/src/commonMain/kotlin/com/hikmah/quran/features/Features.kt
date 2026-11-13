package com.hikmah.quran.features

import kotlinx.coroutines.flow.StateFlow

/** Optional capabilities that need a model file downloaded to the device. */
enum class Feature(val title: String, val description: String) {
    AI(
        "AI commands",
        "Type what you want in your own words, like \"I read Yaseen and Mulk every day\". " +
            "A small language model runs on this device; nothing is sent anywhere.",
    ),
    VOICE(
        "Voice input",
        "Speak instead of typing, using the mic button. Speech is recognised on this device " +
            "and the audio is never stored or sent anywhere.",
    ),
}

sealed interface FeatureStatus {
    /** Switched off. [downloaded] tells whether its model file is still on the device. */
    data class Off(val downloaded: Boolean) : FeatureStatus

    /** [progress] is 0..1, or null before the size is known. [note] explains a pause, e.g. waiting for Wi-Fi. */
    data class Downloading(val progress: Float?, val note: String? = null) : FeatureStatus

    /** Downloaded; being verified and loaded into memory. */
    data object Preparing : FeatureStatus

    data object Ready : FeatureStatus

    data class Failed(val reason: String) : FeatureStatus
}

val FeatureStatus?.isOn: Boolean get() = this != null && this !is FeatureStatus.Off

/** Implemented per platform: owns the switches, the downloads and the loaded models. */
interface FeatureController {
    val status: StateFlow<Map<Feature, FeatureStatus>>

    /** Human-readable download size, e.g. "1.1 GB". */
    fun downloadSize(feature: Feature): String

    /** Turning on downloads the model if needed; turning off cancels a download and unloads the model. */
    fun setEnabled(feature: Feature, enabled: Boolean)

    /** Removes the downloaded model of a feature that is switched off. */
    fun deleteDownload(feature: Feature)
}
