package fi.mazhar.quran.ai.layout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The complete set of things the voice/LLM pipeline is allowed to do.
 * The LLM emits one of these as JSON, e.g.
 * `{"type":"pin_surahs","ids":[36,67,56],"section":"Daily"}`.
 */
@Serializable
sealed interface Action {
    @Serializable @SerialName("pin_surahs")
    data class PinSurahs(val ids: List<Int>, val section: String = DEFAULT_SECTION) : Action

    @Serializable @SerialName("unpin_surahs")
    data class UnpinSurahs(val ids: List<Int>) : Action

    /** Replace the order of a section; ids not already in it are ignored. */
    @Serializable @SerialName("reorder_section")
    data class ReorderSection(val section: String, val ids: List<Int>) : Action

    /** Opens a surah, optionally scrolled to [ayah]. */
    @Serializable @SerialName("open_surah")
    data class OpenSurah(val id: Int, val ayah: Int? = null) : Action

    /** Scrolls the surah that is already open to [ayah]. */
    @Serializable @SerialName("go_to_ayah")
    data class GoToAyah(val ayah: Int) : Action

    @Serializable @SerialName("close_surah")
    data object CloseSurah : Action

    @Serializable @SerialName("set_text_size")
    data class SetTextSize(val size: TextSize) : Action

    @Serializable @SerialName("set_theme")
    data class SetTheme(val theme: AppTheme) : Action

    @Serializable @SerialName("reset_layout")
    data object ResetLayout : Action

    @Serializable @SerialName("no_op")
    data object NoOp : Action

    companion object {
        const val DEFAULT_SECTION = "Daily"

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses model output; anything malformed becomes [NoOp]. */
        fun fromJson(text: String): Action =
            runCatching { json.decodeFromString<Action>(text) }.getOrDefault(NoOp)
    }
}
