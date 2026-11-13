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
    val ayah: Int? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): LlmCommand? =
            runCatching { json.decodeFromString(serializer(), text.trim()) }.getOrNull()

        /** Every field is always present, in this order, so decoding is cheap and total. */
        val GRAMMAR = """
            root ::= "{\"intent\":" intent ",\"surahs\":" names ",\"section\":" optstr ",\"size\":" size ",\"theme\":" theme ",\"ayah\":" ayah "}"
            intent ::= "\"pin\"" | "\"unpin\"" | "\"open\"" | "\"close\"" | "\"text_size\"" | "\"theme\"" | "\"reset\"" | "\"none\""
            names ::= "[]" | "[" str ("," str)* "]"
            optstr ::= "null" | str
            str ::= "\"" [^"\\\n]+ "\""
            size ::= "null" | "\"BIT_BIGGER\"" | "\"BIGGER\"" | "\"MUCH_BIGGER\"" | "\"BIT_SMALLER\"" | "\"SMALLER\"" | "\"MUCH_SMALLER\"" | "\"SMALLEST\"" | "\"SMALL\"" | "\"MEDIUM\"" | "\"LARGE\"" | "\"LARGEST\""
            theme ::= "null" | "\"LIGHT\"" | "\"DARK\"" | "\"SEPIA\""
            ayah ::= "null" | [1-9] [0-9]? [0-9]?
        """.trimIndent()

        val SYSTEM_PROMPT = """
            You control a Quran reading app. The user speaks; you receive an imperfect transcript.
            Reply with one JSON object describing what the app should do.

            intent:
            - "pin": the user says which surahs they recite regularly, or asks to add/pin surahs. Put them at the top.
            - "unpin": the user no longer recites a surah, or asks to remove it from the top.
            - "open": the user wants to read one surah now, or go to a particular ayah (verse).
            - "close": go back to the surah list, also called the main screen or home.
            - "text_size": change the Arabic text size.
            - "theme": change colours.
            - "reset": restore the default layout.
            - "none": anything else, including recitation of the Quran itself, or unclear speech.

            surahs: the surah names exactly as written in the transcript, in order, one string each. Do not correct, translate or number them. Empty unless intent is pin, unpin or open.
            section: always null.
            size: for "text_size" only. Relative: BIGGER or SMALLER; BIT_BIGGER or BIT_SMALLER for "a bit", "a little", "slightly"; MUCH_BIGGER or MUCH_SMALLER for "much", "a lot", "way". Absolute: SMALLEST, SMALL, MEDIUM, LARGE, LARGEST. A complaint describes how the text looks now, so ask for the opposite: "too small", "tiny", "hard to read" mean BIGGER; "too big", "too large" mean SMALLER. Otherwise null.
            theme: for "theme" only: LIGHT, DARK or SEPIA. Otherwise null.
            ayah: for "open" only, the ayah (verse) number as digits if one is mentioned. When an ayah is given without a surah, leave surahs empty: it means the surah already open. Otherwise null.

            Examples:
            I recite surah yaseen and mulk every day
            {"intent":"pin","surahs":["yaseen","mulk"],"section":null,"size":null,"theme":null,"ayah":null}
            on fridays i read al kahf
            {"intent":"pin","surahs":["al kahf"],"section":null,"size":null,"theme":null,"ayah":null}
            i stopped reading wakia
            {"intent":"unpin","surahs":["wakia"],"section":null,"size":null,"theme":null,"ayah":null}
            open surah rahman
            {"intent":"open","surahs":["rahman"],"section":null,"size":null,"theme":null,"ayah":null}
            go to surah fatiha ayat 3
            {"intent":"open","surahs":["fatiha"],"section":null,"size":null,"theme":null,"ayah":3}
            jump to the tenth verse
            {"intent":"open","surahs":[],"section":null,"size":null,"theme":null,"ayah":10}
            take me back to the main screen
            {"intent":"close","surahs":[],"section":null,"size":null,"theme":null,"ayah":null}
            the letters are too small
            {"intent":"text_size","surahs":[],"section":null,"size":"BIGGER","theme":null,"ayah":null}
            switch to night mode
            {"intent":"theme","surahs":[],"section":null,"size":null,"theme":"DARK","ayah":null}
            biggest font please
            {"intent":"text_size","surahs":[],"section":null,"size":"LARGEST","theme":null,"ayah":null}
            just a little bigger
            {"intent":"text_size","surahs":[],"section":null,"size":"BIT_BIGGER","theme":null,"ayah":null}
            the words are tiny, i can hardly see them
            {"intent":"text_size","surahs":[],"section":null,"size":"BIGGER","theme":null,"ayah":null}
            the writing is way too big
            {"intent":"text_size","surahs":[],"section":null,"size":"MUCH_SMALLER","theme":null,"ayah":null}
            my eyes hurt, the screen is so bright
            {"intent":"theme","surahs":[],"section":null,"size":null,"theme":"DARK","ayah":null}
            i want a white background
            {"intent":"theme","surahs":[],"section":null,"size":null,"theme":"LIGHT","ayah":null}
            remove everything i pinned
            {"intent":"reset","surahs":[],"section":null,"size":null,"theme":null,"ayah":null}
            show the list of surahs
            {"intent":"close","surahs":[],"section":null,"size":null,"theme":null,"ayah":null}
            today is friday
            {"intent":"none","surahs":[],"section":null,"size":null,"theme":null,"ayah":null}
            what time is maghrib
            {"intent":"none","surahs":[],"section":null,"size":null,"theme":null,"ayah":null}
        """.trimIndent()
    }
}
