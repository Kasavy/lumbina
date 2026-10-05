package dev.lumbina.tv.platform.wallpaper

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.BackdropPhoto
import dev.lumbina.tv.core.domain.backdrop.BackdropPhotos
import dev.lumbina.tv.core.domain.backdrop.DailyPhoto
import dev.lumbina.tv.core.domain.backdrop.MediaFolder
import dev.lumbina.tv.core.domain.backdrop.PhotoFolder
import dev.lumbina.tv.core.domain.backdrop.PhotosOfTheDay
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperKind
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.network.HttpGet
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The user's pictures and videos: `files/photos/` inside the app (no storage permission needed). */
@Singleton
internal class AppPhotoFolder @Inject constructor(@ApplicationContext context: Context) :
    PhotoFolder,
    MediaFolder {

    private val dir: File = File(context.filesDir, "photos")

    /** Pictures only (the screensaver decodes them itself). */
    fun files(): List<File> = all().filter { it.extension.lowercase() in IMAGE_EXTENSIONS }

    override fun items(): List<WallpaperItem> = all().map { file ->
        val kind = if (file.extension.lowercase() in VIDEO_EXTENSIONS) WallpaperKind.Video else WallpaperKind.Image
        WallpaperItem(Uri.fromFile(file).toString(), kind, title = file.name.substringAfter('-'))
    }

    override fun count(): Int = all().size

    override fun add(name: String, input: InputStream, size: Long): Boolean {
        val safe = name.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.isLetterOrDigit() || it in SAFE_PUNCTUATION }
        if (File(safe).extension.lowercase() !in MEDIA_EXTENSIONS) return false
        dir.mkdirs()
        val file = File(dir, "${System.currentTimeMillis()}-$safe")
        // A dropped connection ends the stream early: keep only what arrived whole.
        val complete = runCatching { file.outputStream().use { input.copyTo(it) } }.getOrNull() == size
        if (!complete) file.delete()
        return complete
    }

    override fun contains(uri: String): Boolean = uri.startsWith(Uri.fromFile(dir).toString() + "/")

    override fun clear() {
        dir.deleteRecursively()
    }

    // Newest first: names start with the time they were added.
    private fun all(): List<File> = dir.listFiles { f ->
        f.isFile && f.extension.lowercase() in MEDIA_EXTENSIONS
    }?.sortedByDescending { it.name }.orEmpty()

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "m4v")
        val MEDIA_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS
        const val SAFE_PUNCTUATION = ".-_ "
    }
}

/**
 * Bing's recent photos of the day (free, no key, reachable where other photo services are not),
 * refreshed every few hours.
 */
@Singleton
internal class BingPhotosOfTheDay @Inject constructor(private val http: HttpGet) : PhotosOfTheDay {
    private val lock = Mutex()
    private var photos: List<DailyPhoto> = emptyList()
    private var fetchedAt = 0L

    override suspend fun photos(width: Int): List<DailyPhoto> = lock.withLock {
        val now = System.currentTimeMillis()
        if (photos.isEmpty() || now - fetchedAt > REFRESH_MILLIS) {
            val json = http.text(ARCHIVE)
            photos = bingPhotos(json, uhd = width > FULL_HD_WIDTH).ifEmpty { throw IOException("no Bing photos") }
            fetchedAt = now
        }
        photos
    }

    private companion object {
        const val FULL_HD_WIDTH = 1920
        const val REFRESH_MILLIS = 6 * 60 * 60_000L
        const val ARCHIVE = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8"
    }
}

/**
 * A few landscapes shipped in the APK (CC0, from Wikimedia Commons; see NOTICE), in turn: what the
 * screensaver shows offline when the user has no pictures of their own.
 */
internal class BundledPhotos @Inject constructor(@ApplicationContext private val context: Context) {
    private var index = 0

    fun next(): ByteArray {
        val photo = PHOTOS[index++ % PHOTOS.size]
        return context.resources.openRawResource(photo).use { it.readBytes() }
    }

    private companion object {
        val PHOTOS = listOf(R.raw.fallback_lake, R.raw.fallback_forest, R.raw.fallback_sunset)
    }
}

/**
 * Photos for the screensaver, following the wallpaper choice: the user's own pictures in random
 * order while the wallpaper is "my pictures and videos" (and there are some), otherwise, unless
 * online photos are off, a random photo from picsum.photos (free, no key), or where picsum
 * refuses (it answers 403 in some countries) one of the photos of the day. Offline or with online
 * photos off: the user's own pictures after all; with none, a few that come with Lumbina.
 */
@Singleton
internal class LocalThenOnlinePhotos @Inject constructor(
    private val folder: AppPhotoFolder,
    private val bundled: BundledPhotos,
    private val photosOfTheDay: PhotosOfTheDay,
    private val wallpaper: WallpaperSettingsRepository,
    private val sleep: SleepSettingsRepository,
    private val http: HttpGet,
    private val log: LumbinaLog,
) : BackdropPhotos {

    private var lastLocal: File? = null

    /** Picsum is skipped until then after a failure: refused (403) or unreachable. */
    private var picsumRetryAt = 0L
    private var lastOnline: String? = null

    override suspend fun next(width: Int, height: Int): BackdropPhoto? = withContext(Dispatchers.IO) {
        if (wallpaper.settings.first().source == WallpaperSource.MyMedia) local()?.let { return@withContext it }
        if (sleep.settings.first().onlinePhotos) online(width, height)?.let { return@withContext it }
        local() ?: BackdropPhoto(bundled.next())
    }

    private fun local(): BackdropPhoto? {
        val files = folder.files()
        if (files.isEmpty()) return null
        val pick = (files - setOfNotNull(lastLocal)).ifEmpty { files }.random()
        lastLocal = pick
        return runCatching { BackdropPhoto(pick.readBytes()) }.getOrNull()
    }

    private suspend fun online(width: Int, height: Int): BackdropPhoto? {
        if (System.currentTimeMillis() >= picsumRetryAt) {
            try {
                return BackdropPhoto(http.bytes("https://picsum.photos/$width/$height"))
            } catch (e: IOException) {
                log.log(TAG, "picsum: $e, photos of the day for now")
                picsumRetryAt = System.currentTimeMillis() + PICSUM_RETRY_MILLIS
            }
        }
        return try {
            val photos = photosOfTheDay.photos(width)
            val pick = photos.filter { it.url != lastOnline }.ifEmpty { photos }.random().also { lastOnline = it.url }
            BackdropPhoto(http.bytes(pick.url), pick.credit)
        } catch (e: IOException) {
            log.log(TAG, "no online photo: $e")
            null
        }
    }

    private companion object {
        const val PICSUM_RETRY_MILLIS = 60 * 60_000L
        const val TAG = "photos"
    }
}

// One photo's entry in Bing's answer: a flat object with "urlbase" and, somewhere in it, "copyright"
// (the place and, in brackets, whose photo it is).
private val BING_ENTRY = Regex("""\{[^{}]*"urlbase"[^{}]*\}""")
private val BING_URL_BASE = Regex(""""urlbase"\s*:\s*"([^"]+)"""")
private val BING_CREDIT = Regex(""""copyright"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private val JSON_ESCAPE = Regex("""\\(u[0-9a-fA-F]{4}|.)""")

/** Full-size photos, each with its credit, from Bing's image archive answer (`HPImageArchive.aspx?format=js`). */
internal fun bingPhotos(json: String, uhd: Boolean): List<DailyPhoto> = BING_ENTRY.findAll(json).mapNotNull { entry ->
    val base = BING_URL_BASE.find(entry.value)?.groupValues?.get(1) ?: return@mapNotNull null
    val credit = BING_CREDIT.find(entry.value)?.groupValues?.get(1).orEmpty()
    DailyPhoto(
        url = "https://www.bing.com${base}_${if (uhd) "UHD" else "1920x1080"}.jpg",
        credit = unescaped(credit).trim().ifEmpty { null },
    )
}.toList()

private fun unescaped(json: String): String = JSON_ESCAPE.replace(json) { match ->
    val escape = match.groupValues[1]
    when {
        escape.length > 1 -> escape.drop(1).toInt(radix = 16).toChar().toString()
        escape in WHITESPACE_ESCAPES -> " "
        else -> escape
    }
}

private val WHITESPACE_ESCAPES = setOf("n", "r", "t", "b", "f")
