package fi.mazhar.quran.ai.ui

/**
 * Adapts the bundled Tanzil text to the KFGQPC Hafs font at display time.
 * The stored text is never changed; this only picks the glyphs the font
 * intends for two marks that it encodes differently from Tanzil:
 *
 *  - the small circle over a silent letter: Tanzil U+06DF, font U+0652
 *  - the sukun (jazm): Tanzil U+0652, font U+06E1
 *
 * Established by aligning the Tanzil text with the King Fahd Complex's own
 * encoding for all 6236 ayahs: every U+06DF there is U+0652, and Tanzil's
 * U+0652 is U+06E1. Without this the font shows a black dotted blob for the
 * first and a round circle instead of the Madinah-style sukun for the second.
 */
internal object KfgqpcHafs {
    private const val TANZIL_SILENT = '\u06DF'
    private const val TANZIL_SUKUN = '\u0652'
    private const val FONT_SILENT = '\u0652'
    private const val FONT_SUKUN = '\u06E1'

    fun glyphs(tanzil: String): String = buildString(tanzil.length) {
        for (c in tanzil) {
            append(
                when (c) {
                    TANZIL_SILENT -> FONT_SILENT
                    TANZIL_SUKUN -> FONT_SUKUN
                    else -> c
                },
            )
        }
    }

    /**
     * The end-of-ayah ornament. In this font the Arabic-Indic digits are
     * themselves drawn as the ornament with the number inside. It is UI
     * decoration, not text; the no-break space keeps it on the ayah's line.
     */
    fun ayahEnd(number: Int): String =
        "\u00A0" + number.toString().map { '\u0660' + (it - '0') }.joinToString("")
}
