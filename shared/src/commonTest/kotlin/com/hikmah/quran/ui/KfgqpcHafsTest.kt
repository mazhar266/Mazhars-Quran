package com.hikmah.quran.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class KfgqpcHafsTest {
    @Test
    fun onlyTheTwoMarksAreRemapped() {
        // waw + silent circle, lam + sukun, plus characters that must pass through untouched
        val tanzil = "\u0648\u06DF \u0644\u0652 \u0671\u0644\u0651\u064E\u0647\u0650 \u06D6 \u064B"
        val shown = KfgqpcHafs.glyphs(tanzil)
        assertEquals("\u0648\u0652 \u0644\u06E1 \u0671\u0644\u0651\u064E\u0647\u0650 \u06D6 \u064B", shown)
        assertEquals(tanzil.length, shown.length)
    }

    @Test
    fun mappingIsReversibleSoNothingIsLost() {
        val tanzil = "\u0628\u0650\u0633\u0652\u0645\u0650 \u0623\u064F\u0648\u06DF\u0644\u064E"
        val back = KfgqpcHafs.glyphs(tanzil).map {
            when (it) {
                '\u0652' -> '\u06DF'
                '\u06E1' -> '\u0652'
                else -> it
            }
        }.joinToString("")
        assertEquals(tanzil, back)
    }

    @Test
    fun ayahEndUsesArabicIndicDigits() {
        assertEquals("\u00A0\u0661\u0662\u0663", KfgqpcHafs.ayahEnd(123))
    }
}
