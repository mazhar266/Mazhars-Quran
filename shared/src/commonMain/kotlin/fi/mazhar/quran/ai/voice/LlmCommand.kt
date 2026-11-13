package fi.mazhar.quran.ai.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An on-device language model. Implementations wrap the native runtime
 * (llama.cpp); [grammar] is a GBNF grammar the output must conform to.
 */
interface LlmEngine {
    suspend fun complete(system: String, user: String, grammar: String): String
}

/**
 * What the model is asked to produce. It only classifies the intent and
 * copies out surah names *as heard*; turning names into surah numbers is
 * done deterministically by [SurahMatcher], which small models are bad at.
 */
@Serializable
data class LlmCommand(
    val intent: String,
    val surahs: List<String> = emptyList(),
    val section: String? = null,
    val size: String? = null,
    val theme: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): LlmCommand? =
            runCatching { json.decodeFromString(serializer(), text.trim()) }.getOrNull()

        /** Every field is always present, in this order, so decoding is cheap and total. */
        val GRAMMAR = """
            root ::= "{\"intent\":" intent ",\"surahs\":" names ",\"section\":" optstr ",\"size\":" size ",\"theme\":" theme "}"
            intent ::= "\"pin\"" | "\"unpin\"" | "\"open\"" | "\"close\"" | "\"text_size\"" | "\"theme\"" | "\"reset\"" | "\"none\""
            names ::= "[]" | "[" str ("," str)* "]"
            optstr ::= "null" | str
            str ::= "\"" [^"\\\n]+ "\""
            size ::= "null" | "\"BIT_BIGGER\"" | "\"BIGGER\"" | "\"MUCH_BIGGER\"" | "\"BIT_SMALLER\"" | "\"SMALLER\"" | "\"MUCH_SMALLER\"" | "\"SMALLEST\"" | "\"SMALL\"" | "\"MEDIUM\"" | "\"LARGE\"" | "\"LARGEST\""
            theme ::= "null" | "\"LIGHT\"" | "\"DARK\"" | "\"SEPIA\""
        """.trimIndent()

        val SYSTEM_PROMPT = """
            You control a Quran reading app. The user speaks; you receive an imperfect transcript.
            Reply with one JSON object describing what the app should do.

            intent:
            - "pin": the user says which surahs they recite regularly, or asks to add/pin surahs. Put them at the top.
            - "unpin": the user no longer recites a surah, or asks to remove it from the top.
            - "open": the user wants to read one surah now.
            - "close": go back to the surah list.
            - "text_size": change the Arabic text size.
            - "theme": change colours.
            - "reset": restore the default layout.
            - "none": anything else, including recitation of the Quran itself, or unclear speech.

            surahs: the surah names exactly as written in the transcript, in order, one string each. Do not correct, translate or number them. Empty unless intent is pin, unpin or open.
            section: always null.
            size: for "text_size" only. Relative: BIGGER or SMALLER; BIT_BIGGER or BIT_SMALLER for "a bit", "a little", "slightly"; MUCH_BIGGER or MUCH_SMALLER for "much", "a lot", "way". Absolute: SMALLEST, SMALL, MEDIUM, LARGE, LARGEST. A complaint describes how the text looks now, so ask for the opposite: "too small", "tiny", "hard to read" mean BIGGER; "too big", "too large" mean SMALLER. Otherwise null.
            theme: for "theme" only: LIGHT, DARK or SEPIA. Otherwise null.

            Examples:
            I recite surah yaseen and mulk every day
            {"intent":"pin","surahs":["yaseen","mulk"],"section":null,"size":null,"theme":null}
            on fridays i read al kahf
            {"intent":"pin","surahs":["al kahf"],"section":null,"size":null,"theme":null}
            i stopped reading wakia
            {"intent":"unpin","surahs":["wakia"],"section":null,"size":null,"theme":null}
            open surah rahman
            {"intent":"open","surahs":["rahman"],"section":null,"size":null,"theme":null}
            the letters are too small
            {"intent":"text_size","surahs":[],"section":null,"size":"BIGGER","theme":null}
            switch to night mode
            {"intent":"theme","surahs":[],"section":null,"size":null,"theme":"DARK"}
            biggest font please
            {"intent":"text_size","surahs":[],"section":null,"size":"LARGEST","theme":null}
            just a little bigger
            {"intent":"text_size","surahs":[],"section":null,"size":"BIT_BIGGER","theme":null}
            the words are tiny, i can hardly see them
            {"intent":"text_size","surahs":[],"section":null,"size":"BIGGER","theme":null}
            the writing is way too big
            {"intent":"text_size","surahs":[],"section":null,"size":"MUCH_SMALLER","theme":null}
            my eyes hurt, the screen is so bright
            {"intent":"theme","surahs":[],"section":null,"size":null,"theme":"DARK"}
            i want a white background
            {"intent":"theme","surahs":[],"section":null,"size":null,"theme":"LIGHT"}
            remove everything i pinned
            {"intent":"reset","surahs":[],"section":null,"size":null,"theme":null}
            show the list of surahs
            {"intent":"close","surahs":[],"section":null,"size":null,"theme":null}
            today is friday
            {"intent":"none","surahs":[],"section":null,"size":null,"theme":null}
            what time is maghrib
            {"intent":"none","surahs":[],"section":null,"size":null,"theme":null}
        """.trimIndent()
    }
}
