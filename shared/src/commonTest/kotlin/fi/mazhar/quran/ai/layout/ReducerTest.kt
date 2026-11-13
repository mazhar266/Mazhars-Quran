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

    @Test
    fun openSurahAtAnAyahAndMoveWithinIt() {
        var state = reduce(empty, Action.OpenSurah(2, 255))
        assertEquals(2, state.openSurah)
        assertEquals(255, state.openAyah)

        val jumps = state.jump
        state = reduce(state, Action.GoToAyah(10))
        assertEquals(10, state.openAyah)
        // Asking for the same ayah again must still count as a new request.
        assertEquals(jumps + 2, reduce(state, Action.GoToAyah(10)).jump)

        assertNull(reduce(state, Action.CloseSurah).openAyah)
    }

    @Test
    fun impossibleAyahsAreIgnoredAndExplained() {
        // Al-Fatihah has 7 ayahs: the surah still opens, at the top.
        val opened = reduce(empty, Action.OpenSurah(1, 9))
        assertEquals(1, opened.openSurah)
        assertNull(opened.openAyah)
        assertEquals("Al-Fatihah has 7 ayahs.", rejection(empty, Action.OpenSurah(1, 9)))

        assertEquals(opened, reduce(opened, Action.GoToAyah(8)))
        assertEquals(empty, reduce(empty, Action.GoToAyah(3)))
        assertEquals("Open a surah first, or say which surah.", rejection(empty, Action.GoToAyah(3)))
        assertNull(rejection(opened, Action.GoToAyah(7)))
    }

    @Test
    fun readerPositionIsNotSaved() {
        val state = reduce(empty, Action.OpenSurah(2, 255))
        assertEquals(empty, decodeLayout(encodeLayout(state)))
    }
}
