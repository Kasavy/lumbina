package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.settings.SettingsRepository
import java.io.InputStream

enum class MediaKind { Photo, Video }

/** What a file sent from a phone is for: the wallpaper and screensaver, or app tiles' banners. */
enum class MediaPurpose { Backdrop, Banner }

/**
 * One of the user's files: [id] names it in its folder and in [MediaMarks]; [title] is the name it
 * came with; [addedAt], wall-clock millis.
 */
data class MediaFile(val id: String, val uri: String, val kind: MediaKind, val title: String, val addedAt: Long)

/**
 * The user's own photos and videos on the device: those for the wallpaper and screensaver, and
 * apart from them the banners, which go on app tiles only. Blocking file work: call it off the
 * main thread.
 */
interface MediaLibrary {
    /** Photos and videos for the wallpaper and screensaver, newest first. */
    fun files(): List<MediaFile>

    /** Photos for app tiles' banners, newest first. */
    fun banners(): List<MediaFile>

    /**
     * Stores a file of [size] bytes read from [input] (not closed here) for [purpose]: a photo or a
     * video the device can play (banners: a photo), when there is [room] for it.
     */
    fun add(name: String, input: InputStream, size: Long, purpose: MediaPurpose): MediaAdd

    /** Bytes the library may still take, leaving the device some room of its own. */
    fun room(): Long

    /** Deletes the files and banners with these ids; unknown ids are skipped. */
    fun delete(ids: Collection<String>)

    /** Moves the photo [id] from the wallpaper's files to the banners: its new URI, null if there is no such photo. */
    fun toBanner(id: String): String?

    /**
     * Copies the photo [id] into the banners under a name of its own, the photo staying where it
     * is: the copy's URI; null if there is no such photo or no room for it.
     */
    fun copyToBanner(id: String): String?
}

/** How adding a file went. */
sealed interface MediaAdd {
    data class Added(val file: MediaFile) : MediaAdd

    /** Not a photo or video the device takes, by its name; a video for a banner. */
    data object NotMedia : MediaAdd

    /** More than the [MediaLibrary.room] left. */
    data object NoRoom : MediaAdd

    /** Cut short (a dropped connection) or not written. */
    data object Incomplete : MediaAdd

    /** A video the device has no decoder for: [what] it is, e.g. "HEVC 3840×2160 10-bit"; "" for no picture at all. */
    data class Unplayable(val what: String) : MediaAdd
}

/**
 * Per file id, where the user took it out: files are on the wallpaper and the screensaver unless
 * listed here, so files sent before the marks existed keep showing as they did. [unplayable]:
 * videos the device failed to play, skipped by the wallpaper and screensaver until the user puts
 * them on either again, for another try.
 */
data class MediaMarks(
    val offWallpaper: Set<String> = emptySet(),
    val offScreensaver: Set<String> = emptySet(),
    val unplayable: Set<String> = emptySet(),
) {
    fun onWallpaper(id: String): Boolean = id !in offWallpaper && id !in unplayable

    fun onScreensaver(id: String): Boolean = id !in offScreensaver && id !in unplayable

    /** Put on, a file also loses the "won't play" mark: another try. */
    fun withWallpaper(id: String, on: Boolean) = if (on) {
        copy(offWallpaper = offWallpaper - id, unplayable = unplayable - id)
    } else {
        copy(offWallpaper = offWallpaper + id)
    }

    fun withScreensaver(id: String, on: Boolean) = if (on) {
        copy(offScreensaver = offScreensaver - id, unplayable = unplayable - id)
    } else {
        copy(offScreensaver = offScreensaver + id)
    }

    fun without(id: String) = MediaMarks(offWallpaper - id, offScreensaver - id, unplayable - id)

    /** Without entries for files that are gone, so the store doesn't grow with every deletion. */
    fun keepOnly(ids: Set<String>) =
        MediaMarks(offWallpaper intersect ids, offScreensaver intersect ids, unplayable intersect ids)
}

typealias MediaMarksRepository = SettingsRepository<MediaMarks>

/**
 * Files lately come from a phone, for home to say so: counted from the first while more keep
 * coming, at most [GAP_MILLIS] apart; [at] is when the last came.
 */
data class MediaArrival(val at: Long = 0, val photos: Int = 0, val videos: Int = 0, val banners: Int = 0) {
    fun with(file: MediaFile, purpose: MediaPurpose, now: Long): MediaArrival {
        val going = if (now - at < GAP_MILLIS) this else MediaArrival()
        return when {
            purpose == MediaPurpose.Banner -> going.copy(at = now, banners = going.banners + 1)
            file.kind == MediaKind.Video -> going.copy(at = now, videos = going.videos + 1)
            else -> going.copy(at = now, photos = going.photos + 1)
        }
    }

    companion object {
        const val GAP_MILLIS = 60_000L
    }
}

typealias MediaArrivalRepository = SettingsRepository<MediaArrival>

/** A file as the wallpaper shows it. */
fun MediaFile.toWallpaperItem() = WallpaperItem(
    uri = uri,
    kind = if (kind == MediaKind.Video) WallpaperKind.Video else WallpaperKind.Image,
    title = title,
)
