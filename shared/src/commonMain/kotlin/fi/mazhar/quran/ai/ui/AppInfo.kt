package fi.mazhar.quran.ai.ui

/** What the About section in Settings shows. The version number comes from the platform build. */
internal object AppInfo {
    const val NAME = "Mazhar's Quran"

    const val AUTHOR = "Mazhar Ahmed"
    val AUTHOR_CREDENTIALS = listOf(
        "BA in Islamic Studies from IOU",
        "Dawra-e-Hadith from Qawmi Madrasa, Bangladesh",
    )

    /** What each part of the app is built on, and whose work it is. */
    val CREDITS = listOf(
        "Quran text" to "Tanzil Project, Uthmani text v1.1 (tanzil.net), Creative Commons Attribution 3.0",
        "Font" to "KFGQPC Hafs Uthmanic Script, King Fahd Glorious Quran Printing Complex",
        "AI commands" to "Qwen2.5 1.5B Instruct by Alibaba Cloud, run on this device with llama.cpp",
        "Voice input" to "Whisper by OpenAI, run on this device with whisper.cpp",
        "Built with" to "Kotlin Multiplatform and Compose Multiplatform by JetBrains",
    )
}
