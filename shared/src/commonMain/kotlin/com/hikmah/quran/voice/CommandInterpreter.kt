package com.hikmah.quran.voice

import com.hikmah.quran.data.SurahCatalog
import com.hikmah.quran.layout.Action
import com.hikmah.quran.layout.AppTheme
import com.hikmah.quran.layout.LayoutState
import com.hikmah.quran.layout.TextSize

/**
 * [confident] false means the app should ask before applying [action]:
 * a surah name only matched approximately, or some names in [unresolved]
 * could not be matched at all.
 */
data class Interpretation(
    val action: Action,
    val confident: Boolean,
    val unresolved: List<String> = emptyList(),
)

/**
 * Turns a transcript into an [Action].
 *
 * Keyword rules run first: when they recognise both a cue word and the surah
 * names exactly, their answer is used and the model is never woken (instant,
 * no battery cost). Everything else goes to the [LlmEngine], which handles
 * free phrasing and filters out speech that is not a command at all.
 * Without an engine (model not downloaded, low-RAM device) or when its
 * output is unusable, the rules' best guess is returned.
 */
class CommandInterpreter(private val llm: LlmEngine? = null) {

    suspend fun interpret(utterance: String, state: LayoutState): Interpretation {
        val byRules = interpretWithRules(utterance, state)
        if (byRules.confident && byRules.action != Action.NoOp) return byRules
        val engine = llm ?: return byRules
        val command = runCatching {
            LlmCommand.parse(engine.complete(LlmCommand.SYSTEM_PROMPT, utterance, LlmCommand.GRAMMAR))
        }.getOrNull() ?: return byRules
        val byModel = fromLlm(command, state, utterance)
        // The rules had a different guess: two readings of the same words, so ask.
        val disputed = byRules.action != Action.NoOp && byModel.action != Action.NoOp &&
            byRules.action != byModel.action
        return if (disputed) byModel.copy(confident = false) else byModel
    }

    companion object {
        private val NONE = Interpretation(Action.NoOp, confident = true)

        /**
         * Maps model output to an action. The model is only trusted for the
         * intent: surah names it did not copy from [utterance] are discarded
         * (it sometimes invents one), and the section name is derived from
         * the utterance rather than taken from the model.
         */
        fun fromLlm(command: LlmCommand, state: LayoutState, utterance: String): Interpretation {
            val heard = words(utterance).toSet()
            val (copied, invented) = command.surahs.partition { name -> words(name).all { it in heard } }
            val matches = copied.map { it to SurahMatcher.resolve(it) }
            val ids = matches.mapNotNull { it.second?.surah }.distinct()
            val unresolved = matches.filter { it.second == null }.map { it.first }
            val sure = invented.isEmpty() && unresolved.isEmpty() && matches.all { it.second?.exact == true }

            val action = when (command.intent) {
                "pin" -> if (ids.isEmpty()) Action.NoOp else Action.PinSurahs(ids, sectionFor(utterance))
                "unpin" -> if (ids.isEmpty()) Action.NoOp else Action.UnpinSurahs(ids)
                "open" -> ids.firstOrNull()?.let(Action::OpenSurah) ?: Action.NoOp
                "close" -> return Interpretation(Action.CloseSurah, confident = true)
                "text_size" -> return sized(command.size.orEmpty(), state)
                "theme" -> return AppTheme.entries.firstOrNull { it.name == command.theme }
                    ?.let { Interpretation(Action.SetTheme(it), confident = true) } ?: NONE
                "reset" -> return Interpretation(Action.ResetLayout, confident = true)
                else -> return NONE
            }
            return Interpretation(action, confident = sure && action != Action.NoOp, unresolved)
        }

        /** Keyword fallback. Deliberately conservative: unknown phrasing becomes a no-op. */
        fun interpretWithRules(utterance: String, state: LayoutState): Interpretation {
            val text = padded(utterance)
            fun has(vararg phrases: String) = phrases.any { " $it " in text }
            val matches = SurahMatcher.findAll(utterance)
            val ids = matches.map { it.surah }
            val sure = matches.all { it.exact }

            if (ids.isEmpty()) {
                // Text size: how far to move depends on the wording ("a bit bigger", "much smaller").
                // A complaint describes the current state: "it's tiny" asks for bigger, not for tiny.
                val up = has(
                    "bigger", "larger", "increase", "zoom in", "too small", "enlarge", "can't read", "cant read",
                    "tiny", "hard to read", "hard to see", "barely see", "hardly see", "can't see", "cant see",
                )
                val down = has("smaller", "decrease", "reduce", "zoom out", "too big", "too large")
                val slight = has("a bit", "bit", "a little", "little", "slightly", "a tad", "a touch", "thora", "thoda")
                val strong = has("much", "a lot", "lot", "way", "far", "bohat", "bahut")
                val amount = if (slight) "BIT_" else if (strong) "MUCH_" else ""
                val size = when {
                    has("biggest", "largest", "maximum", "max", "huge", "extra large", "as big as possible") -> "LARGEST"
                    has("smallest", "minimum", "as small as possible") -> "SMALLEST"
                    up -> amount + "BIGGER"
                    down -> amount + "SMALLER"
                    has("medium", "normal size", "normal text", "default size") -> "MEDIUM"
                    has("large text", "large font", "big text", "big font") -> "LARGE"
                    has("small text", "small font") -> "SMALL"
                    else -> null
                }
                return when {
                    has("reset", "default layout", "start over", "clear my pins", "clear all pins", "clear pins") ->
                        Interpretation(Action.ResetLayout, confident = true)
                    has("dark", "night mode", "black") -> Interpretation(Action.SetTheme(AppTheme.DARK), true)
                    has("sepia", "warm", "paper", "cream") -> Interpretation(Action.SetTheme(AppTheme.SEPIA), true)
                    has("light mode", "light theme", "light background", "day mode", "white") ->
                        Interpretation(Action.SetTheme(AppTheme.LIGHT), true)
                    size != null -> sized(size, state)
                    has("go back", "back to list", "back to the list", "close", "surah list", "all surahs", "home") ->
                        Interpretation(Action.CloseSurah, confident = true)
                    else -> NONE
                }
            }

            val unpin = has(
                "unpin", "remove", "delete", "take off", "stop", "stopped", "no longer", "anymore", "any more",
                "don't", "dont", "do not", "not reciting", "drop", "nahi", "nahin",
            )
            val pin = has(
                "daily", "every day", "everyday", "each day", "every", "always", "regularly", "usually", "often",
                "routine", "pin", "add", "favorite", "favourite", "favorites", "favourites", "top", "first",
                "morning", "night", "evening", "friday", "fridays", "jumma", "juma", "jummah", "bedtime", "sleep",
                "sleeping", "fajr", "maghrib", "isha", "asr prayer", "rozana", "roz", "har", "hamesha",
            )
            // An explicit "open ..." outranks time-of-day words ("open surah fajr").
            val openNow = has("open", "go to", "take me", "show me", "kholo", "dikhao")
            val open = openNow || has("show", "start", "let's read", "lets read", "want to read", "read now", "now")
            val action = when {
                unpin -> Action.UnpinSurahs(ids)
                openNow -> Action.OpenSurah(ids.first())
                pin -> Action.PinSurahs(ids, sectionFor(utterance))
                open || ids.size == 1 -> Action.OpenSurah(ids.first())
                else -> Action.PinSurahs(ids)
            }
            // No cue word at all: we guessed the intent, so ask first.
            return Interpretation(action, confident = sure && (unpin || pin || open))
        }

        private fun padded(utterance: String): String =
            " " + utterance.lowercase().replace(Regex("[^\\p{L}\\p{N}']+"), " ").trim() + " "

        private fun words(text: String): List<String> =
            text.lowercase().replace("'", "").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

        /** Group name for pinned surahs, from when the user says they recite them. */
        private fun sectionFor(utterance: String): String {
            val text = padded(utterance)
            fun has(vararg phrases: String) = phrases.any { " $it " in text }
            return when {
                has("friday", "fridays", "jumma", "juma", "jummah", "jumuah") -> "Friday"
                has("morning", "mornings", "fajr") -> "Morning"
                has("night", "nights", "evening", "bedtime", "sleep", "sleeping", "isha", "maghrib") -> "Night"
                else -> Action.DEFAULT_SECTION
            }
        }

        /**
         * Resolves a size request (the vocabulary the model and the rules share)
         * against the current size. Relative requests move one, two or three of
         * the nine steps; the Settings buttons move one.
         */
        private fun sized(request: String, state: LayoutState): Interpretation {
            val current = state.textSize
            val target = when (request) {
                "BIT_BIGGER" -> current.stepped(1)
                "BIGGER" -> current.stepped(2)
                "MUCH_BIGGER" -> current.stepped(3)
                "BIT_SMALLER" -> current.stepped(-1)
                "SMALLER" -> current.stepped(-2)
                "MUCH_SMALLER" -> current.stepped(-3)
                "SMALLEST" -> TextSize.entries.first()
                "SMALL" -> TextSize.XSMALL
                "MEDIUM" -> TextSize.MEDIUM
                "LARGE" -> TextSize.XLARGE
                "LARGEST" -> TextSize.entries.last()
                else -> return NONE
            }
            return Interpretation(Action.SetTextSize(target), confident = true)
        }

        /** Short summary of an action, for the confirmation and undo snackbars. */
        fun describe(action: Action): String {
            fun names(ids: List<Int>) = ids.mapNotNull { SurahCatalog.byNumber(it)?.transliteration }.joinToString(", ")
            return when (action) {
                is Action.PinSurahs -> "Pin ${names(action.ids)} to ${action.section}"
                is Action.UnpinSurahs -> "Unpin ${names(action.ids)}"
                is Action.ReorderSection -> "Reorder ${action.section}"
                is Action.OpenSurah -> "Open ${names(listOf(action.id))}"
                Action.CloseSurah -> "Back to the list"
                is Action.SetTextSize -> "Text size ${action.size.level} of ${TextSize.entries.size}"
                is Action.SetTheme -> "Theme: ${action.theme.name.lowercase()}"
                Action.ResetLayout -> "Reset layout"
                Action.NoOp -> "Nothing to do"
            }
        }
    }
}
