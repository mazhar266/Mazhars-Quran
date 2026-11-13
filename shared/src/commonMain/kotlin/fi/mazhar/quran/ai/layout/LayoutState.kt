package fi.mazhar.quran.ai.layout

import kotlinx.serialization.Serializable

/** Nine steps of Arabic text size; [MEDIUM] is the default. */
@Serializable
enum class TextSize {
    XXSMALL, XSMALL, SMALL, MEDIUM, LARGE, XLARGE, XXLARGE, XXXLARGE, HUGE;

    /** 1-based position, for showing "5 of 9". */
    val level: Int get() = ordinal + 1

    /** Moves [steps] sizes up (positive) or down (negative), stopping at either end. */
    fun stepped(steps: Int): TextSize = entries[(ordinal + steps).coerceIn(0, entries.lastIndex)]
}

@Serializable
enum class AppTheme { LIGHT, DARK, SEPIA }

/** A named group of surahs shown above the full list, e.g. "Daily". */
@Serializable
data class Section(val name: String, val surahIds: List<Int>)

/**
 * Everything about the UI that can change at runtime. The UI is a pure
 * function of this state; the LLM can only change it through [Action]s.
 */
@Serializable
data class LayoutState(
    val sections: List<Section> = emptyList(),
    val textSize: TextSize = TextSize.MEDIUM,
    val theme: AppTheme = AppTheme.LIGHT,
    /** Surah currently open in the reader, or null for the list. */
    val openSurah: Int? = null,
)
