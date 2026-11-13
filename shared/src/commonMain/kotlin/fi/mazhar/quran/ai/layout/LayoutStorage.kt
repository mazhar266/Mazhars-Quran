package fi.mazhar.quran.ai.layout

import fi.mazhar.quran.ai.data.SurahCatalog
import kotlinx.serialization.json.Json

/**
 * Where the saved layout lives on each platform (SharedPreferences on
 * Android, NSUserDefaults on iOS). It is one small JSON string.
 */
interface LayoutStorage {
    fun read(): String?
    fun write(json: String)
}

/** Keeps nothing; used in previews and tests. */
object NoLayoutStorage : LayoutStorage {
    override fun read(): String? = null
    override fun write(json: String) = Unit
}

private val layoutJson = Json { ignoreUnknownKeys = true }

/** The reader position is not saved: the app always starts on the list. */
fun encodeLayout(state: LayoutState): String =
    layoutJson.encodeToString(LayoutState.serializer(), state.copy(openSurah = null, openAyah = null, jump = 0))

/** Missing, corrupt or out-of-date data falls back to the default layout. */
fun decodeLayout(json: String?): LayoutState {
    if (json == null) return LayoutState()
    val saved = runCatching { layoutJson.decodeFromString(LayoutState.serializer(), json) }
        .getOrNull() ?: return LayoutState()
    val seen = mutableSetOf<Int>()
    val sections = saved.sections.mapNotNull { section ->
        val ids = section.surahIds.filter { SurahCatalog.isValid(it) && seen.add(it) }
        if (ids.isEmpty() || section.name.isBlank()) null else section.copy(surahIds = ids)
    }
    return saved.copy(sections = sections, openSurah = null, openAyah = null, jump = 0)
}
