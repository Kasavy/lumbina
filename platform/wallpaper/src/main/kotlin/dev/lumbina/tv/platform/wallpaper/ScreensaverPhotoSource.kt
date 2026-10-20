package dev.lumbina.tv.platform.wallpaper

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.BackdropPhoto
import dev.lumbina.tv.core.domain.backdrop.BackdropPhotos
import dev.lumbina.tv.core.domain.backdrop.DailyPhoto
import dev.lumbina.tv.core.domain.backdrop.MediaFile
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.domain.backdrop.MediaLibrary
import dev.lumbina.tv.core.domain.backdrop.MediaMarksRepository
import dev.lumbina.tv.core.domain.backdrop.PhotosOfTheDay
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperKind
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.sleep.ScreensaverSource
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.sleep.screensaverSources
import dev.lumbina.tv.core.network.HttpGet
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Photos and videos for the screensaver, from the sources its settings give, in their order
 * ([screensaverSources]): the user's own files, a plugin's, photos of the day (Bing's, the saved
 * ones, picsum.photos where neither is there). Null when none of them gives anything.
 */
@Singleton
internal class ScreensaverPhotoSource @Inject constructor(
    private val library: MediaLibrary,
    private val marks: MediaMarksRepository,
    private val photosOfTheDay: PhotosOfTheDay,
    private val saved: SavedDailyPhotos,
    private val plugins: WallpaperPlugins,
    private val wallpaper: WallpaperSettingsRepository,
    private val sleep: SleepSettingsRepository,
    private val http: HttpGet,
    private val log: LumbinaLog,
) : BackdropPhotos {

    private var lastLocal: String? = null
    private var lastOnline: String? = null

    /** Picsum is skipped until then after a failure: refused (403) or unreachable. */
    private var picsumRetryAt = 0L

    /** A plugin's list, asked again after [PLUGIN_LIST_MILLIS]: asking may bind it and send it online. */
    private var pluginList: Triple<String, Long, List<WallpaperItem>>? = null

    override suspend fun next(width: Int, height: Int, videos: Boolean): BackdropPhoto? = withContext(Dispatchers.IO) {
        val marked = marks.settings.first()
        val sources = screensaverSources(sleep.settings.first().screensaverPhotos, wallpaper.settings.first().source)
        sources.firstNotNullOfOrNull { source ->
            when (source) {
                is ScreensaverSource.OwnFiles -> local(videos) {
                    if (source.forWallpaper) marked.onWallpaper(it.id) else marked.onScreensaver(it.id)
                }

                is ScreensaverSource.Plugin -> plugin(source.id, videos)

                ScreensaverSource.PhotosOfTheDay -> online(width, height)
            }
        }
    }

    /**
     * One of the user's files, photos and [videos] alike, not the one before; a video is played
     * through, not read here. A photo that can't be read gives way to another.
     */
    private fun local(videos: Boolean, wanted: (MediaFile) -> Boolean): BackdropPhoto? {
        val files = library.files().filter { wanted(it) && (videos || it.kind != MediaKind.Video) }
        return turns(files, lastLocal) { it.id }.firstNotNullOfOrNull { pick ->
            val shown = if (pick.kind == MediaKind.Video) {
                BackdropPhoto.Video(pick.uri)
            } else {
                read(pick.uri)?.let { BackdropPhoto.Image(it) }
            }
            shown?.also { lastLocal = pick.id }
        }
    }

    /**
     * One of the images or [videos] the plugin gives by web address, a video to be played through;
     * null when it gives none.
     */
    private suspend fun plugin(id: String, videos: Boolean): BackdropPhoto? {
        val kinds = if (videos) setOf(WallpaperKind.Image, WallpaperKind.Video) else setOf(WallpaperKind.Image)
        val given = pluginItems(id).filter { it.kind in kinds && it.uri.startsWith("http") }
        if (given.isEmpty()) return null
        val pick = turns(given, lastOnline) { it.uri }.first().also { lastOnline = it.uri }
        val credit = pick.credit ?: pick.title
        if (pick.kind == WallpaperKind.Video) return BackdropPhoto.Video(pick.uri, credit)
        return try {
            BackdropPhoto.Image(http.bytes(pick.uri), credit)
        } catch (e: IOException) {
            log.log(TAG, "plugin image: $e")
            null
        }
    }

    private suspend fun pluginItems(id: String): List<WallpaperItem> {
        val now = System.currentTimeMillis()
        pluginList?.let { (listed, at, items) -> if (listed == id && now - at < PLUGIN_LIST_MILLIS) return items }
        return plugins.wallpapers(id).also { pluginList = Triple(id, now, it) }
    }

    /** A photo of the day, from the disk where it was saved; picsum.photos where there is none. */
    private suspend fun online(width: Int, height: Int): BackdropPhoto? {
        try {
            val photos = photosOfTheDay.photos()
            for (pick in turns(photos, lastOnline) { it.url }) {
                val bytes = dailyBytes(pick) ?: continue
                lastOnline = pick.url
                return BackdropPhoto.Image(bytes, pick.credit)
            }
        } catch (e: IOException) {
            log.log(TAG, "photos of the day: $e, picsum instead")
        }
        if (System.currentTimeMillis() < picsumRetryAt) return null
        return try {
            BackdropPhoto.Image(http.bytes("https://picsum.photos/$width/$height"))
        } catch (e: IOException) {
            log.log(TAG, "no online photo: $e")
            picsumRetryAt = System.currentTimeMillis() + PICSUM_RETRY_MILLIS
            null
        }
    }

    /** Null for a saved file that is gone (pruned meanwhile); a failed download throws: the network is down. */
    private suspend fun dailyBytes(photo: DailyPhoto): ByteArray? {
        val file = photo.url.takeIf { it.startsWith("file:") } ?: saved.local(photo)?.url
        return if (file != null) read(file) else http.bytes(photo.url)
    }

    private fun read(uri: String): ByteArray? = try {
        File(URI(uri)).readBytes()
    } catch (e: IOException) {
        log.log(TAG, "not read: $e")
        null
    }

    private companion object {
        const val PICSUM_RETRY_MILLIS = 60 * 60_000L
        const val PLUGIN_LIST_MILLIS = 10 * 60_000L
        const val TAG = "photos"
    }
}

/** [items] in a random order to try, the one shown [last] (by [key]) only at the end: not twice running. */
private fun <T> turns(items: List<T>, last: String?, key: (T) -> String): List<T> {
    val (before, others) = items.partition { key(it) == last }
    return others.shuffled() + before
}
