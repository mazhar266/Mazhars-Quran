package fi.mazhar.quran.ai.layout

import fi.mazhar.quran.ai.data.SurahCatalog

/**
 * Applies an [Action] to a [LayoutState]. Pure and total: invalid surah
 * numbers are dropped, so no model output can produce an invalid state.
 */
fun reduce(state: LayoutState, action: Action): LayoutState = when (action) {
    is Action.PinSurahs -> {
        val ids = action.ids.validIds()
        val name = action.section.trim().ifEmpty { Action.DEFAULT_SECTION }
        if (ids.isEmpty()) state else {
            // A surah lives in at most one section: remove it elsewhere first.
            val cleaned = state.sections.without(ids)
            val existing = cleaned.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val sections = if (existing == null) {
                listOf(Section(name, ids)) + cleaned
            } else {
                cleaned.map { if (it === existing) it.copy(surahIds = it.surahIds + ids) else it }
            }
            state.copy(sections = sections)
        }
    }

    is Action.UnpinSurahs -> state.copy(sections = state.sections.without(action.ids))

    is Action.ReorderSection -> state.copy(
        sections = state.sections.map { section ->
            if (!section.name.equals(action.section.trim(), ignoreCase = true)) section else {
                val requested = action.ids.distinct().filter { it in section.surahIds }
                section.copy(surahIds = requested + (section.surahIds - requested.toSet()))
            }
        },
    )

    is Action.OpenSurah ->
        if (!SurahCatalog.isValid(action.id)) state else state.copy(
            openSurah = action.id,
            // An ayah the surah does not have is ignored: the surah opens at the top.
            openAyah = action.ayah?.takeIf { it in 1..SurahCatalog.all[action.id - 1].ayahCount },
            jump = state.jump + 1,
        )

    is Action.GoToAyah -> {
        val surah = state.openSurah?.let(SurahCatalog::byNumber)
        if (surah == null || action.ayah !in 1..surah.ayahCount) state
        else state.copy(openAyah = action.ayah, jump = state.jump + 1)
    }

    Action.CloseSurah -> state.copy(openSurah = null, openAyah = null)
    is Action.SetTextSize -> state.copy(textSize = action.size)
    is Action.SetTheme -> state.copy(theme = action.theme)
    Action.ResetLayout -> LayoutState(openSurah = state.openSurah, openAyah = state.openAyah, jump = state.jump)
    Action.NoOp -> state
}

private fun List<Int>.validIds(): List<Int> = distinct().filter(SurahCatalog::isValid)

private fun List<Section>.without(ids: List<Int>): List<Section> {
    val remove = ids.toSet()
    return map { it.copy(surahIds = it.surahIds - remove) }.filter { it.surahIds.isNotEmpty() }
}

/**
 * Why [action] cannot be carried out as asked in [state], in words for the
 * user, or null if it can. The reducer itself stays total: it ignores the
 * impossible part.
 */
fun rejection(state: LayoutState, action: Action): String? {
    val (surah, ayah) = when (action) {
        is Action.OpenSurah -> SurahCatalog.byNumber(action.id) to action.ayah
        is Action.GoToAyah ->
            (state.openSurah?.let(SurahCatalog::byNumber) ?: return "Open a surah first, or say which surah.") to action.ayah
        else -> return null
    }
    return if (surah != null && ayah != null && ayah !in 1..surah.ayahCount) {
        "${surah.transliteration} has ${surah.ayahCount} ayahs."
    } else {
        null
    }
}
