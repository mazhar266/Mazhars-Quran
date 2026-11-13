package fi.mazhar.quran.ai.layout

import fi.mazhar.quran.ai.data.SurahCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReducerTest {
    private val empty = LayoutState()

    @Test
    fun catalogIsComplete() {
        assertEquals(SurahCatalog.COUNT, SurahCatalog.all.size)
        assertEquals(SurahCatalog.TOTAL_AYAHS, SurahCatalog.all.sumOf { it.ayahCount })
        assertEquals("Ya-Sin", SurahCatalog.byNumber(36)?.transliteration)
    }

    @Test
    fun pinCreatesSectionAtTop() {
        val state = reduce(empty, Action.PinSurahs(listOf(36, 67, 56)))
        assertEquals(listOf(Section("Daily", listOf(36, 67, 56))), state.sections)
    }

    @Test
    fun pinDropsInvalidAndDuplicateIds() {
        val state = reduce(empty, Action.PinSurahs(listOf(36, 0, 115, 36, -3)))
        assertEquals(listOf(36), state.sections.single().surahIds)
        assertEquals(empty, reduce(empty, Action.PinSurahs(listOf(999))))
    }

    @Test
    fun pinAppendsToExistingSectionAndMovesBetweenSections() {
        var state = reduce(empty, Action.PinSurahs(listOf(36, 67)))
        state = reduce(state, Action.PinSurahs(listOf(18), section = "daily"))
        assertEquals(listOf(36, 67, 18), state.sections.single().surahIds)

        state = reduce(state, Action.PinSurahs(listOf(18), section = "Friday"))
        assertEquals(
            listOf(Section("Friday", listOf(18)), Section("Daily", listOf(36, 67))),
            state.sections,
        )
    }

    @Test
    fun unpinRemovesEmptySections() {
        val pinned = reduce(empty, Action.PinSurahs(listOf(36, 67)))
        assertEquals(listOf(67), reduce(pinned, Action.UnpinSurahs(listOf(36))).sections.single().surahIds)
        assertEquals(emptyList(), reduce(pinned, Action.UnpinSurahs(listOf(36, 67))).sections)
    }

    @Test
    fun reorderKeepsUnmentionedSurahs() {
        val pinned = reduce(empty, Action.PinSurahs(listOf(36, 67, 56)))
        val state = reduce(pinned, Action.ReorderSection("Daily", listOf(56, 2, 36)))
        assertEquals(listOf(56, 36, 67), state.sections.single().surahIds)
    }

    @Test
    fun openAndCloseSurah() {
        assertEquals(18, reduce(empty, Action.OpenSurah(18)).openSurah)
        assertNull(reduce(empty, Action.OpenSurah(200)).openSurah)
        assertNull(reduce(empty.copy(openSurah = 18), Action.CloseSurah).openSurah)
    }

    @Test
    fun parsesModelJson() {
        assertEquals(
            Action.PinSurahs(listOf(36, 67, 56), "Daily"),
            Action.fromJson("""{"type":"pin_surahs","ids":[36,67,56],"section":"Daily"}"""),
        )
        assertEquals(Action.SetTheme(AppTheme.SEPIA), Action.fromJson("""{"type":"set_theme","theme":"SEPIA"}"""))
        assertEquals(Action.NoOp, Action.fromJson("sure! here is your layout"))
        assertEquals(Action.NoOp, Action.fromJson("""{"type":"delete_everything"}"""))
    }
}
