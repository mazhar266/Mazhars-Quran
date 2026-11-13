package com.hikmah.quran.layout

import kotlinx.serialization.Serializable

@Serializable
enum class TextSize { SMALL, MEDIUM, LARGE, XLARGE }

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
