package com.hikmah.quran.data

/**
 * The Arabic text, parsed from the bundled Tanzil Uthmani file
 * (`composeResources/files/quran-uthmani.txt`, kept verbatim, CC BY 3.0).
 * Only obtainable through [parse], which refuses incomplete data.
 */
class QuranText private constructor(private val surahs: List<List<String>>) {

    /** Ayahs of [surahNumber] exactly as in the source file. */
    fun ayahs(surahNumber: Int): List<String> = surahs[surahNumber - 1]

    /**
     * Ayahs split for display. In the source, ayah 1 of every surah except
     * Al-Fatihah (where it is the ayah itself) and At-Tawbah (which has none)
     * is prefixed with the basmala; here it is separated out as a heading.
     */
    fun display(surahNumber: Int): SurahDisplay {
        val ayahs = ayahs(surahNumber)
        if (surahNumber == 1 || surahNumber == 9) return SurahDisplay(null, ayahs)
        val words = ayahs.first().split(' ')
        return SurahDisplay(
            basmala = words.take(BASMALA_WORDS).joinToString(" "),
            ayahs = listOf(words.drop(BASMALA_WORDS).joinToString(" ")) + ayahs.drop(1),
        )
    }

    companion object {
        const val RESOURCE_PATH = "files/quran-uthmani.txt"
        const val SHA256 = "4b91f9e6e8ac645d039e4ed85b3be492e795232a31cd22d668ac58238722e26f"
        const val BASMALA_WORDS = 4

        /**
         * Parses Tanzil's `sura|aya|text` format. Returns null unless every
         * surah is present, in order, with the ayah count from [SurahCatalog].
         */
        fun parse(raw: String): QuranText? {
            val surahs = List(SurahCatalog.COUNT) { mutableListOf<String>() }
            for (line in raw.lineSequence()) {
                if (line.isBlank() || line.startsWith('#')) continue
                val parts = line.split('|', limit = 3)
                if (parts.size != 3) return null
                val surah = parts[0].toIntOrNull() ?: return null
                val ayah = parts[1].toIntOrNull() ?: return null
                if (!SurahCatalog.isValid(surah)) return null
                val list = surahs[surah - 1]
                if (ayah != list.size + 1 || parts[2].isEmpty()) return null
                list += parts[2]
            }
            val complete = SurahCatalog.all.all { surahs[it.number - 1].size == it.ayahCount }
            return if (complete) QuranText(surahs) else null
        }
    }
}

class SurahDisplay(val basmala: String?, val ayahs: List<String>)
