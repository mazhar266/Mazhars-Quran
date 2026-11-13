package com.hikmah.quran.voice

import com.hikmah.quran.layout.Action
import com.hikmah.quran.layout.AppTheme
import com.hikmah.quran.layout.LayoutState
import com.hikmah.quran.layout.TextSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandInterpreterTest {
    private val state = LayoutState()

    @Test
    fun matcherHandlesSpellingVariants() {
        fun ids(text: String) = SurahMatcher.findAll(text).map { it.surah }
        assertEquals(listOf(36), ids("yaseen"))
        assertEquals(listOf(36), ids("ya sin"))
        assertEquals(listOf(56, 67), ids("surah wakia and al-mulk"))
        assertEquals(listOf(3), ids("surah aal e imran"))
        assertEquals(listOf(2), ids("al bakarah"))
        assertEquals(listOf(18), ids("سورة الكهف"))
        assertEquals(listOf(112), ids("surah 112"))
        assertEquals(emptyList(), ids("I have 36 books in my room and I feel sad"))
        assertEquals(emptyList(), ids("after fajr and asr on jumma"))
    }

    @Test
    fun matcherResolvesModelSpans() {
        assertEquals(SurahMatcher.Match(36, exact = true), SurahMatcher.resolve("surah ya seen"))
        assertEquals(SurahMatcher.Match(38, exact = true), SurahMatcher.resolve("sad"))
        assertEquals(SurahMatcher.Match(67, exact = true), SurahMatcher.resolve("67"))
        assertEquals(SurahMatcher.Match(73, exact = false), SurahMatcher.resolve("muzamel"))
        assertNull(SurahMatcher.resolve("ayatul kursi"))
        assertNull(SurahMatcher.resolve("200"))
    }

    @Test
    fun modelOutputBecomesAnAction() {
        fun run(json: String, heard: String = "on friday i read yaseen and al mulk and ayatul kursi xyz") =
            CommandInterpreter.fromLlm(LlmCommand.parse(json)!!, state, heard)

        val pin = run("""{"intent":"pin","surahs":["yaseen","al mulk"],"section":"friday","size":null,"theme":null}""")
        assertEquals(Action.PinSurahs(listOf(36, 67), "Friday"), pin.action)
        assertTrue(pin.confident)

        // An unknown name is dropped and makes the result need confirmation.
        val partial = run("""{"intent":"pin","surahs":["yaseen","ayatul kursi"],"section":null,"size":null,"theme":null}""")
        assertEquals(Action.PinSurahs(listOf(36), "Friday"), partial.action)
        assertFalse(partial.confident)
        assertEquals(listOf("ayatul kursi"), partial.unresolved)

        // A surah the user never said is discarded, however plausible.
        val invented = run("""{"intent":"pin","surahs":["al-kawthar"]}""", heard = "it is jumma today")
        assertEquals(Action.NoOp, invented.action)

        assertEquals(Action.NoOp, run("""{"intent":"open","surahs":["xyz"],"section":null,"size":null,"theme":null}""").action)
        assertEquals(
            Action.SetTextSize(TextSize.LARGE),
            run("""{"intent":"text_size","surahs":[],"section":null,"size":"BIGGER","theme":null}""").action,
        )
        assertEquals(
            Action.SetTheme(AppTheme.SEPIA),
            run("""{"intent":"theme","surahs":[],"section":null,"size":null,"theme":"SEPIA"}""").action,
        )
        assertEquals(Action.NoOp, run("""{"intent":"launch_missiles"}""").action)
        assertNull(LlmCommand.parse("I think the user wants..."))
    }

    /**
     * Scores the keyword fallback on the eval set. The floor guards against
     * regressions; it is not a quality bar, and the rules were written with
     * this set in view, so real-world accuracy will be lower.
     */
    @Test
    fun ruleFallbackScoreOnEvalSet() {
        val failures = EvalSet.cases.mapNotNull { case ->
            val actual = CommandInterpreter.interpretWithRules(case.utterance, state).action
            if (actual == case.expected) null else "  \"${case.utterance}\"\n    expected ${case.expected}\n    actual   $actual"
        }
        val passed = EvalSet.cases.size - failures.size
        println("Rule fallback: $passed/${EvalSet.cases.size} correct")
        failures.forEach(::println)
        assertTrue(passed >= RULE_FLOOR, "Rule fallback regressed: $passed/${EvalSet.cases.size}")
    }

    private companion object {
        const val RULE_FLOOR = 75
    }
}
