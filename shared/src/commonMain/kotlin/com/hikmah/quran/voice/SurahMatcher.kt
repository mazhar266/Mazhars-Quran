package com.hikmah.quran.voice

import com.hikmah.quran.data.SurahCatalog

/**
 * Deterministic surah-name recognition. Speech-to-text mangles Arabic names
 * ("yaseen", "ya sin", "wakia"), so names are compared after a phonetic
 * normalisation and, where safe, with a small edit distance.
 *
 * Used two ways: to resolve the name spans the LLM extracts, and on its own
 * as the no-LLM fallback (see [CommandInterpreter]).
 */
object SurahMatcher {
    /** [exact] is false when the name only matched approximately. */
    data class Match(val surah: Int, val exact: Boolean)

    /** Resolves one name as extracted by the LLM, e.g. "ya seen" or "36". */
    fun resolve(name: String): Match? {
        val tokens = tokenize(name).filterNot { it in MARKERS || it in ARTICLES || it in SKIPPABLE }
        if (tokens.isEmpty()) return null
        tokens.singleOrNull()?.toIntOrNull()?.let { number ->
            return if (SurahCatalog.isValid(number)) Match(number, exact = true) else null
        }
        return lookup(key(tokens.joinToString("")), fuzzy = true)
            ?: scan(tokens, alwaysMarked = true).firstOrNull()
    }

    /** Finds every surah mentioned in free text, in spoken order, without duplicates. */
    fun findAll(text: String): List<Match> = scan(tokenize(text), alwaysMarked = false)

    private fun scan(tokens: List<String>, alwaysMarked: Boolean): List<Match> {
        val found = LinkedHashMap<Int, Match>()
        var marked = alwaysMarked
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                // "surah ..." / "al-..." announce that a name follows.
                token in MARKERS || token in NAME_ARTICLES -> { marked = true; i++; continue }
                token in ARTICLES -> { i++; continue }
                token in SKIPPABLE && marked -> { i++; continue }
            }
            var consumed = 0
            val number = token.toIntOrNull()
            if (number != null) {
                if (marked && SurahCatalog.isValid(number)) {
                    found.getOrPut(number) { Match(number, exact = true) }
                    consumed = 1
                }
            } else {
                for (n in minOf(3, tokens.size - i) downTo 1) {
                    val words = tokens.subList(i, i + n)
                    if (words.any { it.toIntOrNull() != null }) continue
                    val candidate = key(words.joinToString(""))
                    // Words like "room" or "jumma" are only names when announced as one.
                    if (n == 1 && !marked && (token in AMBIGUOUS || candidate.length <= 1)) continue
                    val match = lookup(candidate, fuzzy = marked || (n == 1 && candidate.length >= 7)) ?: continue
                    found.getOrPut(match.surah) { match }
                    consumed = n
                    break
                }
            }
            i += maxOf(consumed, 1)
            marked = alwaysMarked
        }
        return found.values.toList()
    }

    private fun lookup(key: String, fuzzy: Boolean): Match? {
        if (key.isEmpty()) return null
        ALIASES[key]?.let { return Match(it, exact = true) }
        if (!fuzzy || key.first() !in 'a'..'z') return null
        val limit = when {
            key.length <= 4 -> return null
            key.length <= 6 -> 1
            else -> 2
        }
        var best = limit + 1
        var surah: Int? = null
        for ((alias, number) in ALIASES) {
            if (kotlin.math.abs(alias.length - key.length) > limit) continue
            val distance = editDistance(key, alias, limit)
            if (distance < best) { best = distance; surah = number }
            else if (distance == best && number != surah) surah = null // ambiguous
        }
        return surah?.let { Match(it, exact = false) }
    }

    private fun tokenize(text: String): List<String> = buildList {
        val current = StringBuilder()
        fun flush() { if (current.isNotEmpty()) { add(current.toString()); current.clear() } }
        for (raw in text.lowercase()) {
            when {
                raw in APOSTROPHES || raw in ARABIC_MARKS -> Unit // drop without splitting the word
                raw in 'a'..'z' || raw.isArabicLetter() -> {
                    if (current.isNotEmpty() && current.last().isDigit()) flush()
                    current.append(raw)
                }
                raw in '0'..'9' -> {
                    if (current.isNotEmpty() && !current.last().isDigit()) flush()
                    current.append(raw)
                }
                raw in ARABIC_DIGITS -> {
                    if (current.isNotEmpty() && !current.last().isDigit()) flush()
                    current.append('0' + (raw - ARABIC_DIGITS.first))
                }
                else -> flush()
            }
        }
        flush()
    }

    /** Phonetic key: spelling variants of the same name should collapse to one string. */
    private fun key(joined: String): String {
        if (joined.any { it.isArabicLetter() }) {
            val plain = joined
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
                .replace('ة', 'ه').replace('ى', 'ي').replace('ی', 'ي').replace('ک', 'ك')
            return if (plain.startsWith("ال") && plain.length > 2) plain.drop(2) else plain
        }
        val swapped = joined
            .replace("q", "k").replace("ph", "f")
            .replace("ee", "i").replace("oo", "u").replace("ou", "u")
        val collapsed = buildString {
            for (c in swapped) if (isEmpty() || last() != c) append(c)
        }
        return if (collapsed.length > 3 && collapsed.endsWith("ah")) collapsed.dropLast(1) else collapsed
    }

    private fun editDistance(a: String, b: String, limit: Int): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val row = IntArray(b.length + 1)
            row[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                row[j] = minOf(previous[j] + 1, row[j - 1] + 1, previous[j - 1] + cost)
                rowMin = minOf(rowMin, row[j])
            }
            if (rowMin > limit) return limit + 1
            previous = row
        }
        return previous[b.length]
    }

    private const val APOSTROPHES = "'’ʼ`ʻ"
    private val ARABIC_EXTRA = setOf('ٱ', 'ی', 'ک')
    private val ARABIC_MARKS = setOf('ـ', 'ٰ') + ('ً'..'ْ')
    private val ARABIC_DIGITS = '٠'..'٩'

    private fun Char.isArabicLetter(): Boolean = this in 'ء'..'ي' || this in ARABIC_EXTRA

    private val MARKERS = setOf(
        "surah", "sura", "surat", "soorah", "sorah", "surahs", "suras", "chapter", "سورة", "سوره", "سورت",
    )
    /** Articles that are not also English words: seeing one means a name follows. */
    private val NAME_ARTICLES = setOf("al", "ar", "ash", "az", "adh", "el", "ul")
    private val ARTICLES = setOf("an", "as", "at", "ad")
    private val SKIPPABLE = setOf("number", "no", "num", "e")

    /** English or everyday words that are also a surah name once normalised. */
    private val AMBIGUOUS = setOf(
        "sad", "room", "rum", "feel", "fill", "teen", "tin", "tour", "rad", "hood", "ala", "saba",
        "juma", "jumma", "jummah", "jumah", "jumuah", "haj", "hajj", "fajr", "asr", "duha",
    )

    /** Extra spellings per surah number, beyond the catalog transliteration. */
    private val EXTRA = mapOf(
        1 to "fatiha fateha alhamd alhamdu", 2 to "baqara bakara bakra", 3 to "imran aliimran aleimran aalimran",
        4 to "nisa", 5 to "maida maeda", 6 to "anam", 7 to "araf", 8 to "anfal",
        9 to "tawba tauba toba baraah", 10 to "yunus younus", 11 to "hud", 12 to "yusuf yousuf yusef",
        13 to "rad raad", 14 to "ibrahim ibraheem", 15 to "hijr", 16 to "nahl",
        17 to "isra baniisrail baniisrael", 18 to "kahf kahaf kehf", 19 to "maryam mariam maryum",
        20 to "taha tuaha", 21 to "anbiya ambiya", 22 to "hajj haj", 23 to "muminun mominoon mumineen",
        24 to "nur noor", 25 to "furqan furkan", 26 to "shuara", 27 to "naml", 28 to "qasas",
        29 to "ankabut ankaboot", 30 to "rum room", 31 to "luqman lukman", 32 to "sajdah sajda sijdah",
        33 to "ahzab", 34 to "saba", 35 to "fatir", 36 to "yasin yaseen yaasin yassin",
        37 to "saffat", 38 to "sad saad suad", 39 to "zumar", 40 to "ghafir mumin momin",
        41 to "fussilat hamimsajdah hameemsajda", 42 to "shura shoora", 43 to "zukhruf", 44 to "dukhan",
        45 to "jathiyah jasiya", 46 to "ahqaf", 47 to "muhammad mohammad mohammed", 48 to "fath fatah",
        49 to "hujurat hujraat", 50 to "qaf", 51 to "dhariyat zariyat", 52 to "tur toor", 53 to "najm",
        54 to "qamar", 55 to "rahman rehman", 56 to "waqiah waqia wakia waqiya waqiyah", 57 to "hadid hadeed",
        58 to "mujadilah mujadala", 59 to "hashr", 60 to "mumtahanah mumtahina", 61 to "saff saf",
        62 to "jumuah juma jumma jumah", 63 to "munafiqun munafiqoon munafiqeen", 64 to "taghabun",
        65 to "talaq", 66 to "tahrim tahreem", 67 to "mulk tabarak", 68 to "qalam", 69 to "haqqah haaqa",
        70 to "maarij", 71 to "nuh nooh noah", 72 to "jinn", 73 to "muzzammil muzammil",
        74 to "muddaththir muddassir mudassir muddathir", 75 to "qiyamah qayamat", 76 to "insan dahr",
        77 to "mursalat", 78 to "naba", 79 to "naziat", 80 to "abasa", 81 to "takwir takweer",
        82 to "infitar", 83 to "mutaffifin mutaffifeen", 84 to "inshiqaq", 85 to "buruj burooj",
        86 to "tariq", 87 to "ala", 88 to "ghashiyah", 89 to "fajr", 90 to "balad", 91 to "shams",
        92 to "layl lail", 93 to "duha zuha dhuha", 94 to "sharh inshirah alamnashrah", 95 to "tin teen",
        96 to "alaq iqra", 97 to "qadr", 98 to "bayyinah", 99 to "zalzalah zilzal", 100 to "adiyat",
        101 to "qariah", 102 to "takathur takasur", 103 to "asr", 104 to "humazah", 105 to "fil feel",
        106 to "quraysh quraish", 107 to "maun maoon", 108 to "kawthar kausar kauthar kosar",
        109 to "kafirun kafiroon", 110 to "nasr", 111 to "masad lahab tabbat", 112 to "ikhlas",
        113 to "falaq", 114 to "nas",
    )

    private val ALIASES: Map<String, Int> = buildMap {
        for (surah in SurahCatalog.all) {
            val latin = tokenize(surah.transliteration)
            put(key(latin.joinToString("")), surah.number)
            put(key(latin.dropWhile { it in NAME_ARTICLES || it in ARTICLES }.joinToString("")), surah.number)
            put(key(tokenize(surah.arabicName).joinToString("")), surah.number)
            EXTRA[surah.number]?.split(' ')?.forEach { put(key(it), surah.number) }
        }
        remove("")
    }
}
