package dev.lumbina.tv.platform.wallpaper

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.DailyPhoto
import dev.lumbina.tv.core.domain.backdrop.PhotosOfTheDay
import dev.lumbina.tv.core.network.HttpGet
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where photos of the day are downloaded aside, outliving whoever asked for them. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SavingScope

/**
 * Bing's recent photos of the day (free, no key, reachable where other photo services are not),
 * refreshed every few hours, by the same addresses until then: the wallpaper finds the one it
 * shows and goes on from it. Each is also kept on disk as it comes ([SavedDailyPhotos]); those
 * are what Bing's list gives way to when it can't be fetched, e.g. offline, and Bing is not asked
 * again for a while: every round would otherwise wait out the network's timeouts.
 */
@Singleton
internal class BingPhotosOfTheDay @Inject constructor(
    private val http: HttpGet,
    private val saved: SavedDailyPhotos,
    private val log: LumbinaLog,
    @SavingScope private val scope: CoroutineScope,
) : PhotosOfTheDay {
    private val lock = Mutex()
    private var photos: List<DailyPhoto> = emptyList()
    private var fetchedAt = 0L
    private var failedAt = 0L

    override suspend fun photos(): List<DailyPhoto> = lock.withLock {
        val now = System.currentTimeMillis()
        if (photos.isNotEmpty() && now - fetchedAt < REFRESH_MILLIS) return@withLock photos
        if (now - failedAt < RETRY_MILLIS) return@withLock offline(IOException("Bing failed lately"))
        try {
            photos = bingPhotos(http.text(ARCHIVE)).ifEmpty { throw IOException("no Bing photos") }
            fetchedAt = now
        } catch (e: IOException) {
            log.log(TAG, "Bing: $e, the saved photos instead")
            failedAt = now
            return@withLock offline(e)
        }
        // Downloaded aside: the wallpaper shows the first by its address without waiting for all.
        val fresh = photos
        scope.launch { saved.keep(fresh) }
        photos
    }

    private suspend fun offline(failure: IOException): List<DailyPhoto> =
        saved.all().ifEmpty { photos }.ifEmpty { throw failure }

    private companion object {
        const val REFRESH_MILLIS = 6 * 60 * 60_000L
        const val RETRY_MILLIS = 10 * 60_000L
        const val ARCHIVE = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8"
        const val TAG = "photos"
    }
}

// One photo's entry in Bing's answer: a flat object with "urlbase" and, somewhere in it, "copyright"
// (the place and, in brackets, whose photo it is).
private val BING_ENTRY = Regex("""\{[^{}]*"urlbase"[^{}]*\}""")
private val BING_URL_BASE = Regex(""""urlbase"\s*:\s*"([^"]+)"""")
private val BING_CREDIT = Regex(""""copyright"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private val JSON_ESCAPE = Regex("""\\(u[0-9a-fA-F]{4}|.)""")

/** Full HD photos, each with its credit, from Bing's image archive answer (`HPImageArchive.aspx?format=js`). */
internal fun bingPhotos(json: String): List<DailyPhoto> = BING_ENTRY.findAll(json).mapNotNull { entry ->
    val base = BING_URL_BASE.find(entry.value)?.groupValues?.get(1) ?: return@mapNotNull null
    val credit = BING_CREDIT.find(entry.value)?.groupValues?.get(1).orEmpty()
    DailyPhoto(
        url = "https://www.bing.com${base}_1920x1080.jpg",
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
