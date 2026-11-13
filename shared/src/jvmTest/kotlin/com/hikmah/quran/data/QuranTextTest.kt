package com.hikmah.quran.data

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QuranTextTest {
    private val bytes = File("src/commonMain/composeResources/${QuranText.RESOURCE_PATH}").readBytes()
    private val raw = bytes.decodeToString()

    @Test
    fun bundledFileIsTheVerifiedTanzilCopy() {
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(QuranText.SHA256, sha, "The bundled Quran text has been modified")
    }

    @Test
    fun parsesEverySurahWithCatalogAyahCounts() {
        val text = assertNotNull(QuranText.parse(raw))
        SurahCatalog.all.forEach { assertEquals(it.ayahCount, text.ayahs(it.number).size, it.transliteration) }
    }

    @Test
    fun displaySplitsOffExactlyTheBasmala() {
        val text = assertNotNull(QuranText.parse(raw))
        val shadda = "ّ"
        val basmala = text.ayahs(1).first()

        assertNull(text.display(1).basmala)
        assertNull(text.display(9).basmala)
        for (surah in SurahCatalog.all.filter { it.number != 1 && it.number != 9 }) {
            val display = text.display(surah.number)
            // 95 and 97 carry an extra assimilation shadda on the first letter,
            // so compare with shaddas ignored on both sides.
            assertEquals(
                basmala.replace(shadda, ""),
                display.basmala?.replace(shadda, ""),
                surah.transliteration,
            )
            assertEquals(surah.ayahCount, display.ayahs.size)
            // Nothing is lost or altered by the split.
            assertEquals(
                text.ayahs(surah.number).first(),
                "${display.basmala} ${display.ayahs.first()}",
            )
        }
    }

    @Test
    fun rejectsIncompleteOrMalformedText() {
        assertNull(QuranText.parse(""))
        assertNull(QuranText.parse(raw.lines().drop(1).joinToString("\n")))
        assertNull(QuranText.parse(raw.replaceFirst("2|5|", "2|6|")))
    }

    @Test
    fun bundledFontHasAGlyphForEveryCharacterShown() {
        val font = java.awt.Font.createFont(
            java.awt.Font.TRUETYPE_FONT,
            File("src/commonMain/composeResources/font/amiri_quran.ttf"),
        )
        val text = assertNotNull(QuranText.parse(raw))
        val shown = buildSet {
            SurahCatalog.all.forEach { surah ->
                text.ayahs(surah.number).forEach { addAll(it.asIterable()) }
                addAll(surah.arabicName.asIterable())
            }
            addAll("﴾﴿".asIterable()) // verse-number brackets
            addAll(('٠'..'٩')) // Arabic-Indic digits
        }
        val missing = shown.filterNot(font::canDisplay).map { "U+%04X".format(it.code) }
        assertEquals(emptyList(), missing)
    }
}
