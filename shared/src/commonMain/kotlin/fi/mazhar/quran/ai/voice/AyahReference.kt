package fi.mazhar.quran.ai.voice

/**
 * An ayah mentioned in a command. [surah] is only set by the "2:255" form;
 * otherwise the surah comes from the name in the sentence or the open surah.
 */
data class AyahReference(val surah: Int?, val ayah: Int)

/**
 * Finds "ayah 3", "ayat number 255", "verse twenty one", "the third verse",
 * "3rd ayah" or "2:255" in a transcript. Returns null if no ayah is mentioned.
 */
internal fun findAyahReference(utterance: String): AyahReference? {
    COLON_FORM.find(utterance)?.let { match ->
        val surah = match.groupValues[1].toIntOrNull()
        val ayah = match.groupValues[2].toIntOrNull()
        if (surah != null && ayah != null && ayah > 0) return AyahReference(surah, ayah)
    }
    val words = utterance.lowercase().replace("'", "").split(SEPARATORS).filter { it.isNotEmpty() }
    val keyword = words.indexOfFirst { it in AYAH_WORDS }
    if (keyword < 0) return null

    // "ayah [number] 12" / "verse twenty one"
    var next = keyword + 1
    while (next < words.size && words[next] in FILLERS) next++
    numberAt(words, next)?.let { return AyahReference(null, it) }

    // "the third verse" / "3rd ayah" / "12 ayah"
    val before = words.getOrNull(keyword - 1) ?: return null
    val number = ORDINALS[before] ?: before.removeSuffixes().toIntOrNull()
    return number?.takeIf { it > 0 }?.let { AyahReference(null, it) }
}

/** Reads a number written as digits or as English words ("two hundred and fifty five") starting at [start]. */
private fun numberAt(words: List<String>, start: Int): Int? {
    val first = words.getOrNull(start) ?: return null
    first.removeSuffixes().toIntOrNull()?.let { return it.takeIf { n -> n > 0 } }
    ORDINALS[first]?.let { return it }
    var total = 0
    var found = false
    var i = start
    while (i < words.size) {
        val word = words[i]
        val value = UNITS[word] ?: TENS[word]
        when {
            value != null -> total += value
            word == "hundred" -> total = (if (total == 0) 1 else total) * 100
            word == "and" && found && (words.getOrNull(i + 1)?.let { it in UNITS || it in TENS } == true) -> Unit
            else -> break
        }
        found = true
        i++
    }
    return total.takeIf { found && it > 0 }
}

/** "3rd" -> "3", "255th" -> "255". */
private fun String.removeSuffixes(): String =
    if (length > 2 && first().isDigit() && takeLast(2) in ORDINAL_SUFFIXES) dropLast(2) else this

private val COLON_FORM = Regex("(?<![\\p{L}\\p{N}])(\\d{1,3})\\s*:\\s*(\\d{1,3})(?![\\p{L}\\p{N}])")
private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
private val AYAH_WORDS = setOf(
    "ayah", "ayat", "aya", "ayaat", "ayath", "ayahs", "ayas", "verse", "verses",
    "\u0622\u064A\u0629", "\u0627\u064A\u0629", "\u0627\u064A\u0647", "\u0622\u06CC\u062A", "\u0627\u06CC\u062A",
)
private val FILLERS = setOf("number", "no", "num")
private val ORDINAL_SUFFIXES = setOf("st", "nd", "rd", "th")
private val UNITS = mapOf(
    "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
    "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
    "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
)
private val TENS = mapOf(
    "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
    "eighty" to 80, "ninety" to 90,
)
private val ORDINALS = mapOf(
    "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7,
    "eighth" to 8, "ninth" to 9, "tenth" to 10,
)
