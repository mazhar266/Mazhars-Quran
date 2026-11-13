package com.hikmah.quran.voice

import com.hikmah.quran.layout.Action
import com.hikmah.quran.layout.Action.CloseSurah
import com.hikmah.quran.layout.Action.NoOp
import com.hikmah.quran.layout.Action.OpenSurah
import com.hikmah.quran.layout.Action.PinSurahs
import com.hikmah.quran.layout.Action.ResetLayout
import com.hikmah.quran.layout.Action.SetTextSize
import com.hikmah.quran.layout.Action.SetTheme
import com.hikmah.quran.layout.Action.UnpinSurahs
import com.hikmah.quran.layout.AppTheme
import com.hikmah.quran.layout.TextSize

/**
 * Transcripts as speech-to-text might produce them, with the action the app
 * should take. Evaluated from the default layout (medium text, list open).
 * The same set scores the keyword rules and, later, each candidate model.
 */
object EvalSet {
    data class Case(val utterance: String, val expected: Action)

    private infix fun String.gives(action: Action) = Case(this, action)

    val cases: List<Case> = listOf(
        // Pin
        "I daily recite surah yaseen, surah mulk and surah waqiah" gives PinSurahs(listOf(36, 67, 56)),
        "i recite yasin and mulk every day" gives PinSurahs(listOf(36, 67)),
        "every day i read ya seen and al waqia" gives PinSurahs(listOf(36, 56)),
        "pin surah kahf" gives PinSurahs(listOf(18)),
        "add ar rahman to my favourites" gives PinSurahs(listOf(55)),
        "on fridays i read surah kahf" gives PinSurahs(listOf(18), "Friday"),
        "every night before sleeping i recite surah mulk" gives PinSurahs(listOf(67), "Night"),
        "every morning i read yaseen" gives PinSurahs(listOf(36), "Morning"),
        "I always recite al-baqarah" gives PinSurahs(listOf(2)),
        "main rozana surah yaseen parhta hoon" gives PinSurahs(listOf(36)),
        "put surah rahman and surah waqia at the top" gives PinSurahs(listOf(55, 56)),
        "my daily surahs are fatiha, ikhlas, falaq and nas" gives PinSurahs(listOf(1, 112, 113, 114)),
        "I usually read surah 36 and surah 67" gives PinSurahs(listOf(36, 67)),
        "after fajr i recite surah yasin" gives PinSurahs(listOf(36), "Morning"),
        "i recite sura al mulk and sura as sajda every night" gives PinSurahs(listOf(67, 32), "Night"),
        "daily i read surah muzzammil" gives PinSurahs(listOf(73)),
        "add kahf and yaseen" gives PinSurahs(listOf(18, 36)),
        "I read surah al kahf every jumma" gives PinSurahs(listOf(18), "Friday"),
        "surah waqiah after maghrib every day" gives PinSurahs(listOf(56), "Night"),
        "i regularly recite surah ar-rahman, al-waqi'ah and al-mulk" gives PinSurahs(listOf(55, 56, 67)),
        "every day I recite Yaseen Sharif" gives PinSurahs(listOf(36)),
        "favorite surahs: duha, sharh, tin" gives PinSurahs(listOf(93, 94, 95)),
        "I recite tabarak every night" gives PinSurahs(listOf(67), "Night"),
        "i read sura wakiya daily" gives PinSurahs(listOf(56)),
        "daily surah rehman" gives PinSurahs(listOf(55)),
        "pin surah kaaf" gives PinSurahs(listOf(50)),
        "pin surat al mulk" gives PinSurahs(listOf(67)),
        "i recite surah yassin and surah al mook every day" gives PinSurahs(listOf(36, 67)),

        // Unpin
        "i no longer recite surah mulk" gives UnpinSurahs(listOf(67)),
        "remove yaseen from the top" gives UnpinSurahs(listOf(36)),
        "unpin waqiah" gives UnpinSurahs(listOf(56)),
        "i stopped reading surah kahf" gives UnpinSurahs(listOf(18)),
        "i don't read surah rahman daily anymore" gives UnpinSurahs(listOf(55)),
        "take surah mulk off my list" gives UnpinSurahs(listOf(67)),
        "delete baqarah from favourites" gives UnpinSurahs(listOf(2)),

        // Open
        "open surah rahman" gives OpenSurah(55),
        "go to al kahf" gives OpenSurah(18),
        "show me surah yusuf" gives OpenSurah(12),
        "i want to read surah maryam" gives OpenSurah(19),
        "surah yaseen" gives OpenSurah(36),
        "open chapter 18" gives OpenSurah(18),
        "let's read al mulk now" gives OpenSurah(67),
        "take me to surah baqara" gives OpenSurah(2),
        "open yaseen" gives OpenSurah(36),
        "read surah number 112" gives OpenSurah(112),
        "open surah fajr" gives OpenSurah(89),
        "surah ya sin kholo" gives OpenSurah(36),
        "open sad" gives OpenSurah(38),
        "open surah sad" gives OpenSurah(38),
        "افتح سورة الكهف" gives OpenSurah(18),
        "سورة يس" gives OpenSurah(36),

        // Close
        "go back" gives CloseSurah,
        "back to the list" gives CloseSurah,
        "close this surah" gives CloseSurah,
        "show all surahs" gives CloseSurah,

        // Text size (from MEDIUM)
        "make the text bigger" gives SetTextSize(TextSize.LARGE),
        "the letters are too small" gives SetTextSize(TextSize.LARGE),
        "increase font size" gives SetTextSize(TextSize.LARGE),
        "make it smaller" gives SetTextSize(TextSize.SMALL),
        "text is too big" gives SetTextSize(TextSize.SMALL),
        "use the largest text" gives SetTextSize(TextSize.XLARGE),
        "medium text size please" gives SetTextSize(TextSize.MEDIUM),
        "i can't read this, it's tiny" gives SetTextSize(TextSize.LARGE),
        "zoom in" gives SetTextSize(TextSize.LARGE),

        // Theme
        "switch to dark mode" gives SetTheme(AppTheme.DARK),
        "night mode please" gives SetTheme(AppTheme.DARK),
        "sepia theme" gives SetTheme(AppTheme.SEPIA),
        "light mode" gives SetTheme(AppTheme.LIGHT),
        "make the background white" gives SetTheme(AppTheme.LIGHT),
        "it's too bright" gives SetTheme(AppTheme.DARK),

        // Reset
        "reset the layout" gives ResetLayout,
        "clear my pins" gives ResetLayout,
        "start over" gives ResetLayout,

        // Nothing to do: chatter, unsupported requests, and recitation itself
        "what time is maghrib" gives NoOp,
        "hello" gives NoOp,
        "translate this for me" gives NoOp,
        "i feel sad today" gives NoOp,
        "it is jumma today" gives NoOp,
        "play some music" gives NoOp,
        "pin ayatul kursi" gives NoOp,
        "bismillah ir rahman ir rahim" gives NoOp,
        "alhamdulillahi rabbil alameen" gives NoOp,
        "qul huwa allahu ahad" gives NoOp,
    )
}
