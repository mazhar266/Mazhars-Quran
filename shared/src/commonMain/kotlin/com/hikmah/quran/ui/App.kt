package com.hikmah.quran.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikmah.quran.data.QuranText
import com.hikmah.quran.data.Surah
import com.hikmah.quran.data.SurahCatalog
import com.hikmah.quran.data.SurahDisplay
import com.hikmah.quran.generated.Res
import com.hikmah.quran.generated.kfgqpc_hafs
import com.hikmah.quran.features.Feature
import com.hikmah.quran.features.FeatureController
import com.hikmah.quran.features.FeatureStatus
import com.hikmah.quran.features.isOn
import com.hikmah.quran.layout.Action
import com.hikmah.quran.layout.AppTheme
import com.hikmah.quran.layout.LayoutState
import com.hikmah.quran.layout.LayoutStorage
import com.hikmah.quran.layout.NoLayoutStorage
import com.hikmah.quran.layout.decodeLayout
import com.hikmah.quran.layout.encodeLayout
import com.hikmah.quran.layout.TextSize
import com.hikmah.quran.layout.reduce
import com.hikmah.quran.voice.CommandInterpreter
import com.hikmah.quran.voice.VoiceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

/**
 * Root composable. Holds the [LayoutState] and is the single place where
 * [Action]s are applied; the voice pipeline will call the same `dispatch`.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalComposeUiApi::class)
@Composable
fun App(
    storage: LayoutStorage = NoLayoutStorage,
    interpreter: CommandInterpreter = CommandInterpreter(),
    voice: VoiceInput? = null,
    features: FeatureController? = null,
) {
    val statuses = features?.status?.collectAsState()?.value ?: emptyMap()
    var showSettings by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(decodeLayout(storage.read())) }
    // Save whenever anything other than the open surah changes (undo included).
    val saved = encodeLayout(state)
    LaunchedEffect(saved) { storage.write(saved) }
    var text by remember { mutableStateOf<TextLoad>(TextLoad.Loading) }
    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.Default) {
            runCatching { QuranText.parse(Res.readBytes(QuranText.RESOURCE_PATH).decodeToString()) }
                .getOrNull()?.let(TextLoad::Ready) ?: TextLoad.Failed
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun dispatch(action: Action, undoLabel: String? = null) {
        val before = state
        state = reduce(state, action)
        if (undoLabel != null && state != before) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(undoLabel, "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) state = before
            }
        }
    }

    var thinking by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }

    /** Entry point for a transcript, typed or spoken. */
    suspend fun interpretAndApply(utterance: String, spoken: Boolean) {
            thinking = true
            val result = try {
                interpreter.interpret(utterance, state)
            } finally {
                thinking = false
            }
            val label = CommandInterpreter.describe(result.action)
            val navigation = result.action is Action.OpenSurah || result.action == Action.CloseSurah
            snackbar.currentSnackbarData?.dismiss()
            when {
                // Speech that isn't a command (talking, recitation) is ignored quietly.
                result.action == Action.NoOp ->
                    if (!spoken) snackbar.showSnackbar("Sorry, I didn't understand that.")
                result.confident ->
                    if (reduce(state, result.action) == state) snackbar.showSnackbar("That's already how it is.")
                    else dispatch(result.action, if (navigation) null else "Done: $label")
                else -> {
                    // Not sure (guessed intent or approximate surah name): ask first.
                    val answer = snackbar.showSnackbar(
                        "Did you mean: $label?", "Yes",
                        withDismissAction = true, duration = SnackbarDuration.Long,
                    )
                    if (answer == SnackbarResult.ActionPerformed) dispatch(result.action, null)
                }
            }
    }

    fun runCommand(utterance: String) {
        heard = null
        scope.launch { interpretAndApply(utterance, spoken = false) }
    }

    /** Mic button: first tap starts listening, second tap stops and acts on what was said. */
    fun toggleMic() {
        val input = voice ?: return
        scope.launch {
            if (!listening) {
                heard = null
                if (input.start()) listening = true
                else snackbar.showSnackbar("Voice input isn't available (no speech model, or no microphone access).")
            } else {
                listening = false
                thinking = true
                val transcript = try {
                    input.finish()
                } finally {
                    thinking = false
                }
                heard = transcript ?: "(nothing heard)"
                if (transcript != null) interpretAndApply(transcript, spoken = true)
            }
        }
    }

    // With a feature controller the command bar only exists once the user turns
    // something on; without one (iOS for now) typed commands are always offered.
    val micReady = voice != null && (features == null || statuses[Feature.VOICE] == FeatureStatus.Ready)
    val commandsOn = features == null || statuses[Feature.AI].isOn || statuses[Feature.VOICE].isOn
    LaunchedEffect(micReady) {
        if (!micReady && listening) {
            voice?.cancel()
            listening = false
        }
    }

    BackHandler(enabled = showSettings || state.openSurah != null) {
        if (showSettings) showSettings = false else dispatch(Action.CloseSurah)
    }

    MaterialTheme(colorScheme = state.theme.colors()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (commandsOn && !showSettings) {
                    CommandBar(thinking, listening, heard, ::runCommand, if (micReady) ::toggleMic else null)
                }
            },
        ) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding)) {
                val open = state.openSurah?.let(SurahCatalog::byNumber)
                when {
                    showSettings -> SettingsScreen(
                        features = features,
                        statuses = statuses,
                        textSize = state.textSize,
                        onTextSize = { dispatch(Action.SetTextSize(it)) },
                        onBack = { showSettings = false },
                    )
                    open == null -> SurahList(state, ::dispatch) { showSettings = true }
                    else -> Reader(open, text, state.textSize, ::dispatch)
                }
            }
        }
    }
}

/** Where commands are typed or, with voice input on, spoken. */
@Composable
private fun CommandBar(
    thinking: Boolean,
    listening: Boolean,
    heard: String?,
    onCommand: (String) -> Unit,
    onMic: (() -> Unit)?,
) {
    var input by remember { mutableStateOf("") }
    fun send() {
        val command = input.trim()
        if (command.isNotEmpty()) {
            onCommand(command)
            input = ""
        }
    }
    Surface(tonalElevation = 3.dp) {
      Column(Modifier.navigationBarsPadding().imePadding()) {
        // Shown while the on-device model is working on a command.
        if (thinking) LinearProgressIndicator(Modifier.fillMaxWidth())
        val status = when {
            listening -> "Listening… tap ■ when you're done"
            heard != null -> "Heard: $heard"
            else -> null
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Type what you would say") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            TextButton(onClick = ::send, enabled = !thinking && !listening) { Text("Send") }
            if (onMic != null) {
                TextButton(onClick = onMic, enabled = !thinking) {
                    Text(if (listening) "■" else "🎤", fontSize = 22.sp)
                }
            }
        }
      }
    }
}

@Composable
private fun SurahList(
    state: LayoutState,
    dispatch: (Action, String?) -> Unit,
    onSettings: () -> Unit,
) {
    val pinned = state.sections.flatMap { it.surahIds }.toSet()
    val listState = rememberLazyListState()
    // Pinned sections live at the top; when they change, bring them into view
    // so the effect of a (spoken) command is always visible.
    var shownSections by remember { mutableStateOf(state.sections) }
    LaunchedEffect(state.sections) {
        if (state.sections != shownSections) {
            shownSections = state.sections
            listState.animateScrollToItem(0)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item(key = "settings") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSettings) { Text("Settings") }
            }
        }
        state.sections.forEach { section ->
            item(key = "header-${section.name}") { SectionHeader(section.name) }
            items(section.surahIds, key = { "pin-$it" }) { id ->
                SurahCatalog.byNumber(id)?.let { surah ->
                    SurahRow(surah, pinned = true, state.textSize, dispatch)
                }
            }
        }
        if (state.sections.isNotEmpty()) {
            item(key = "header-all") { SectionHeader("All surahs") }
        }
        items(SurahCatalog.all.filter { it.number !in pinned }, key = { it.number }) { surah ->
            SurahRow(surah, pinned = false, state.textSize, dispatch)
        }
        item(key = "attribution") {
            Text(
                "Quran text: Tanzil Project (Uthmani, v1.1) · tanzil.net\n" +
                    "Font: KFGQPC Hafs Uthmanic Script · King Fahd Glorious Quran Printing Complex",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SurahRow(
    surah: Surah,
    pinned: Boolean,
    textSize: TextSize,
    dispatch: (Action, String?) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dispatch(Action.OpenSurah(surah.number), null) }
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = surah.number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(surah.transliteration, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${surah.ayahCount} ayahs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(surah.arabicName, fontSize = textSize.listSp(), fontFamily = quranFont())
            // Manual pin toggle: exercises the same actions voice will emit.
            TextButton(
                onClick = {
                    if (pinned) {
                        dispatch(Action.UnpinSurahs(listOf(surah.number)), "Unpinned ${surah.transliteration}")
                    } else {
                        dispatch(Action.PinSurahs(listOf(surah.number)), "Pinned ${surah.transliteration}")
                    }
                },
            ) { Text(if (pinned) "★" else "☆", fontSize = 20.sp) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** State of the bundled text: it is parsed off the main thread at startup. */
private sealed interface TextLoad {
    data object Loading : TextLoad
    data object Failed : TextLoad
    data class Ready(val text: QuranText) : TextLoad
}

@Composable
private fun Reader(
    surah: Surah,
    text: TextLoad,
    textSize: TextSize,
    dispatch: (Action, String?) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { dispatch(Action.CloseSurah, null) }) { Text("‹ Surahs") }
            TextSizeButtons(textSize) { dispatch(Action.SetTextSize(it), null) }
            Text(
                surah.arabicName,
                fontSize = 22.sp,
                fontFamily = quranFont(),
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        HorizontalDivider()
        when (text) {
            TextLoad.Loading -> Notice("Loading…")
            // Never show text that failed verification.
            TextLoad.Failed -> Notice("The Quran text could not be verified, so it is not shown.")
            is TextLoad.Ready -> Ayahs(remember(surah.number) { text.text.display(surah.number) }, textSize)
        }
    }
}

@Composable
private fun Notice(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The surah as continuous text: ayahs follow one another on the same line and
 * wrap at the edge of the screen, as in a printed mushaf. Long surahs are laid
 * out in several large paragraphs (see [SurahDisplay.paragraphs]) so that
 * opening Al-Baqarah does not have to lay out the whole surah at once.
 */
@Composable
private fun Ayahs(display: SurahDisplay, textSize: TextSize) {
    val size = textSize.readerSp()
    val font = quranFont()
    val paragraphs = remember(display) {
        display.paragraphs(PARAGRAPH_CHARS).map { range ->
            range.joinToString(" ") { index ->
                KfgqpcHafs.glyphs(display.ayahs[index]) + KfgqpcHafs.ayahEnd(index + 1)
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            display.basmala?.let { basmala ->
                item(key = "basmala") {
                    Text(
                        text = KfgqpcHafs.glyphs(basmala),
                        fontSize = size,
                        fontFamily = font,
                        lineHeight = size * LINE_HEIGHT,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
            items(paragraphs.size) { index ->
                Text(
                    text = paragraphs[index],
                    fontSize = size,
                    fontFamily = font,
                    lineHeight = size * LINE_HEIGHT,
                    // With only two or three words per line, justifying leaves wide gaps.
                    textAlign = if (textSize <= TextSize.XLARGE) TextAlign.Justify else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }
    }
}

private const val PARAGRAPH_CHARS = 4000
private const val LINE_HEIGHT = 2.0f

/** KFGQPC Hafs: the typeface of the Madinah mushaf, from the King Fahd Complex. */
@Composable
internal fun quranFont(): FontFamily = FontFamily(Font(Res.font.kfgqpc_hafs))

/** Arabic surah names in the list, by [TextSize] step. */
internal fun TextSize.listSp(): TextUnit = (18 + 2 * ordinal).sp

/** Ayah text in the reader, by [TextSize] step: 18 sp to 52 sp, 28 sp by default. */
internal fun TextSize.readerSp(): TextUnit = READER_SP[ordinal].sp

private val READER_SP = intArrayOf(18, 21, 24, 28, 32, 36, 41, 46, 52)

/** "A-" and "A+" buttons that move the text size one step. */
@Composable
internal fun TextSizeButtons(size: TextSize, onChange: (TextSize) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(size.stepped(-1)) }, enabled = size != TextSize.entries.first()) {
            Text("A−", fontSize = 15.sp)
        }
        TextButton(onClick = { onChange(size.stepped(1)) }, enabled = size != TextSize.entries.last()) {
            Text("A+", fontSize = 21.sp)
        }
    }
}

private fun AppTheme.colors() = when (this) {
    AppTheme.LIGHT -> lightColorScheme(primary = Color(0xFF1B6B4A))
    AppTheme.DARK -> darkColorScheme(primary = Color(0xFF7FD8AE))
    AppTheme.SEPIA -> lightColorScheme(
        primary = Color(0xFF7A5A2B),
        background = Color(0xFFF5ECD9),
        surface = Color(0xFFF5ECD9),
        onBackground = Color(0xFF3B2F1E),
        onSurface = Color(0xFF3B2F1E),
        outlineVariant = Color(0xFFDCCFB4),
    )
}
