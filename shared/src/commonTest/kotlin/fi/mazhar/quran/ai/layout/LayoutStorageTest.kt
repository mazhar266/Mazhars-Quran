package fi.mazhar.quran.ai.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutStorageTest {
    @Test
    fun roundTripsEverythingExceptTheOpenSurah() {
        val state = LayoutState(
            sections = listOf(Section("Daily", listOf(36, 67, 56)), Section("Friday", listOf(18))),
            textSize = TextSize.LARGE,
            theme = AppTheme.SEPIA,
            openSurah = 18,
        )
        assertEquals(state.copy(openSurah = null), decodeLayout(encodeLayout(state)))
    }

    @Test
    fun missingOrCorruptDataGivesTheDefaultLayout() {
        assertEquals(LayoutState(), decodeLayout(null))
        assertEquals(LayoutState(), decodeLayout(""))
        assertEquals(LayoutState(), decodeLayout("{not json"))
        assertEquals(LayoutState(), decodeLayout("""{"theme":"NEON"}"""))
    }

    @Test
    fun dropsInvalidAndDuplicateSurahsOnLoad() {
        val json = """{"sections":[
            {"name":"Daily","surahIds":[36,0,36,200]},
            {"name":"Other","surahIds":[36,999]},
            {"name":" ","surahIds":[2]}
        ],"futureField":true}"""
        assertEquals(listOf(Section("Daily", listOf(36))), decodeLayout(json).sections)
    }
}
