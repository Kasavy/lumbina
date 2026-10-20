package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.settings.SettingsRepository

/** Where the home screen's wallpaper comes from. */
sealed interface WallpaperSource {
    /** The built-in gradient chosen in the home appearance. */
    data object Gradient : WallpaperSource

    /** The user's own photos and videos ([MediaLibrary]) with the wallpaper mark. */
    data object MyMedia : WallpaperSource

    /** Photos of the day from the internet. */
    data object PhotoOfTheDay : WallpaperSource

    /** A wallpaper plugin, by [WallpaperPluginInfo.id]. */
    data class Plugin(val id: String) : WallpaperSource
}

data class WallpaperSettings(
    val source: WallpaperSource = WallpaperSource.Gradient,
    val changeMinutes: Int = DEFAULT_CHANGE_MINUTES,
    /** Darker wallpaper late in the evening and at night, easier on the eyes in a dark room. */
    val dimAtNight: Boolean = true,
    /** Changed when new media arrive, so the wallpaper shows them now rather than next round. */
    val mediaChangedAt: Long = 0,
    /** The file to show first when the user's own are cycled, e.g. one just chosen in the library. */
    val startWith: String? = null,
) {
    companion object {
        const val DEFAULT_CHANGE_MINUTES = 15
    }
}

typealias WallpaperSettingsRepository = SettingsRepository<WallpaperSettings>

enum class WallpaperKind { Image, Video, Color }

/**
 * One wallpaper: an image or video URI (http, content, file, android.resource) or a `#RRGGBB` color.
 * [credit] is the line its source asks to be shown with it (Bing: the place and whose photo it is).
 */
data class WallpaperItem(
    val uri: String,
    val kind: WallpaperKind,
    val title: String? = null,
    val credit: String? = null,
)

/** A plugin: [id] is `package/service`; [settingsActivity], its settings screen's full class name, if it has one. */
data class WallpaperPluginInfo(val id: String, val name: String, val settingsActivity: String? = null) {
    val hasSettings: Boolean get() = settingsActivity != null
    val packageName: String get() = id.substringBefore('/')
}

/** Wallpaper plugins: separate apps implementing an open wallpaper plugin API. */
interface WallpaperPlugins {
    fun installed(): List<WallpaperPluginInfo>

    /** The plugin's current wallpapers; empty when it has none or cannot be reached. */
    suspend fun wallpapers(id: String): List<WallpaperItem>

    fun openSettings(id: String): Boolean
}

/** A photo of the day: its image URL and the [credit] its source gives (the place, the author). */
data class DailyPhoto(val url: String, val credit: String? = null)

/** Recent photos of the day (e.g. Bing's), full HD. */
fun interface PhotosOfTheDay {
    suspend fun photos(): List<DailyPhoto>
}
