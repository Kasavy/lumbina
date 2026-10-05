package dev.lumbina.tv.core.domain.text

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** An installed app that opens YouTube links: the official one, or another client. */
data class YouTubeApp(val packageName: String, val label: String)

/** The app the user chose for YouTube; null (or one no longer installed) means the first found. */
data class YouTubeSettings(val app: String? = null)

/** Persisted [YouTubeSettings], shared by the launcher UI and the remote's process. */
typealias YouTubeSettingsRepository = SettingsRepository<YouTubeSettings>

/** The apps on the device that open YouTube links, the official one first. */
fun interface YouTubeApps {
    suspend fun installed(): List<YouTubeApp>
}

/** What there is to choose from, and the app in use; [chosen] is null only when there is none. */
data class YouTubeChoice(val apps: List<YouTubeApp>, val chosen: YouTubeApp?)

/**
 * Where YouTube opens: the home screen's search, a voice search and links from the phone go to
 * the app the user chose among those installed.
 */
class YouTube @Inject constructor(private val apps: YouTubeApps, private val settings: YouTubeSettingsRepository) {

    val choice: Flow<YouTubeChoice> = settings.settings.map { chosen ->
        val installed = apps.installed()
        YouTubeChoice(installed, installed.chosen(chosen))
    }

    suspend fun app(): YouTubeApp? = apps.installed().chosen(settings.settings.first())

    suspend fun use(app: YouTubeApp) {
        settings.update { it.copy(app = app.packageName) }
    }
}

/** The app in use: the chosen one while it is installed, else the first. */
fun List<YouTubeApp>.chosen(settings: YouTubeSettings): YouTubeApp? =
    firstOrNull { it.packageName == settings.app } ?: firstOrNull()

/**
 * The apps people may call by voice, with [tube], the app in use for YouTube, answering to
 * "YouTube" whatever it is called. Another app of that name gives it up, or the two could not
 * be told apart: it is still opened from the home screen, or by a voice name the user gives it.
 */
fun List<App>.withYouTube(tube: App?): List<App> =
    if (tube == null) this else filterNot { it.label.equals(YOUTUBE, ignoreCase = true) } + tube.copy(label = YOUTUBE)

private const val YOUTUBE = "YouTube"
