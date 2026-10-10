package dev.lumbina.tv.core.domain.text

import dev.lumbina.tv.core.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where a search goes. */
enum class SearchTarget {
    /** The device's own search (Google's on Android TV), which finds a title across apps. */
    System,

    /** YouTube in the app chosen for it ([YouTube]). */
    YouTube,
}

/** The device's own search, where it has one. */
interface SystemSearch {
    suspend fun available(): Boolean

    /** Opens it for the user to type in; false if there is none. */
    fun open(): Boolean

    /** Has it look for [query]; false if there is none that takes one. */
    fun search(query: String): Boolean
}

/** Where the user chose to search; null until they choose. */
data class SearchSettings(val target: SearchTarget? = null)

/** Persisted [SearchSettings], shared by the launcher UI and the remote's process. */
typealias SearchSettingsRepository = SettingsRepository<SearchSettings>

/** What there is to choose from, and where a search goes; [chosen] is null only when there is nowhere. */
data class SearchChoice(val targets: List<SearchTarget>, val chosen: SearchTarget?)

/**
 * Where a search goes: the home screen's search button, and a phrase said on a launcher that is
 * no command. The user's choice among what the device has; until one is made, the system's search
 * where there is one (it looks in every app), else YouTube.
 */
class Search @Inject constructor(
    private val system: SystemSearch,
    private val youTube: YouTube,
    private val youTubeSearch: YouTubeSearch,
    private val settings: SearchSettingsRepository,
) {
    val choice: Flow<SearchChoice> = settings.settings.map { chosen(it) }

    suspend fun use(target: SearchTarget) {
        settings.update { it.copy(target = target) }
    }

    /** Opens the system's search for the user to type in; false if there is none. */
    fun openSystem(): Boolean = system.open()

    /**
     * Looks for [query] there; false when there is nowhere, or it didn't open (a system's search
     * that opens to type in need not take a query from outside).
     */
    suspend fun search(query: String): Boolean = when (chosen(settings.settings.first()).chosen) {
        SearchTarget.System -> system.search(query)
        SearchTarget.YouTube -> youTubeSearch.search(query)
        null -> false
    }

    private suspend fun chosen(settings: SearchSettings): SearchChoice {
        val targets = listOfNotNull(
            SearchTarget.System.takeIf { system.available() },
            SearchTarget.YouTube.takeIf { youTube.app() != null },
        )
        return SearchChoice(targets, settings.target?.takeIf { it in targets } ?: targets.firstOrNull())
    }
}
