package dev.lumbina.tv.core.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.Options
import coil3.toAndroidUri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Path.Companion.toOkioPath

/**
 * Coil's image loader for Lumbina, downloading through Android's own HttpURLConnection rather than
 * OkHttp, whose reads can stall on some networks where the platform client doesn't. What comes
 * from the web or from another app's provider is kept on disk and served from there first: a
 * poster rarely changes under the same address, and the device may be offline or the other app
 * not reachable yet — the image is still there.
 */
fun lumbinaImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build() }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("images").toOkioPath())
            .maxSizeBytes(DISK_CACHE_BYTES)
            .build()
    }
    .components {
        add(UrlConnectionFetcher.Factory())
        add(ForeignContentFetcher.Factory())
    }
    .build()

/** The system may clear it when storage runs low. */
private const val DISK_CACHE_BYTES = 100L * 1024 * 1024
private const val MEMORY_CACHE_BYTES = 64L * 1024 * 1024

/**
 * The image kept on disk under [key]; failing that, [load]s it and keeps it. A copy that can't
 * be written is still shown.
 */
private suspend fun cachedOrLoad(
    imageLoader: ImageLoader,
    options: Options,
    key: String,
    loaded: DataSource,
    load: () -> Buffer,
): FetchResult = withContext(Dispatchers.IO) {
    val cache = imageLoader.diskCache
    val policy = options.diskCachePolicy
    if (cache != null && policy.readEnabled) {
        cache.openSnapshot(key)?.let { return@withContext fromDisk(cache, key, it) }
    }
    val body = load()
    if (cache != null && policy.writeEnabled) {
        cache.openEditor(key)?.let { editor ->
            var snapshot: DiskCache.Snapshot? = null
            try {
                cache.fileSystem.write(editor.data) { writeAll(body.copy()) }
                snapshot = editor.commitAndOpenSnapshot()
            } catch (_: IOException) {
                // Shown from memory all the same.
            } finally {
                if (snapshot == null) runCatching { editor.abort() }
            }
            if (snapshot != null) return@withContext fromDisk(cache, key, snapshot)
        }
    }
    SourceFetchResult(ImageSource(body, options.fileSystem), mimeType = null, dataSource = loaded)
}

private fun fromDisk(cache: DiskCache, key: String, snapshot: DiskCache.Snapshot) = SourceFetchResult(
    source = ImageSource(snapshot.data, cache.fileSystem, diskCacheKey = key, closeable = snapshot),
    mimeType = null,
    dataSource = DataSource.DISK,
)

private class UrlConnectionFetcher(
    private val url: String,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        cachedOrLoad(imageLoader, options, options.diskCacheKey ?: url, DataSource.NETWORK, ::download)

    private fun download(): Buffer {
        val connection = connect(URL(url))
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $url")
            return Buffer().apply { connection.inputStream.use { readFrom(it) } }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens [start], following up to [MAX_REDIRECTS] redirects by hand: the platform client
     * won't switch protocols on its own, and image hosts often redirect http to https.
     * Only web redirects count, and never from https down to http.
     */
    private fun connect(start: URL): HttpURLConnection {
        var target = start
        repeat(MAX_REDIRECTS + 1) {
            val connection = target.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            val location = connection.getHeaderField("Location")
            if (connection.responseCode !in REDIRECTS || location == null) return connection
            connection.disconnect()
            val next = URL(target, location)
            val allowed = next.protocol == "https" || (next.protocol == "http" && target.protocol == "http")
            if (!allowed) throw IOException("Refused redirect to ${next.protocol} for $url")
            target = next
        }
        throw IOException("Too many redirects for $url")
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "http" ||
                data.scheme == "https"
            ) {
                UrlConnectionFetcher(data.toString(), options, imageLoader)
            } else {
                null
            }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
        const val MAX_REDIRECTS = 5
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * Images from other apps' content providers: reading one starts the app's process, which some
 * firmware refuses for a while after boot. The TV provider (channel logos, which change under the
 * same address) and Lumbina's own are left to Coil, read fresh each time.
 */
private class ForeignContentFetcher(
    private val uri: Uri,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        cachedOrLoad(imageLoader, options, options.diskCacheKey ?: uri.toString(), DataSource.DISK) {
            val stream = options.context.contentResolver.openInputStream(uri.toAndroidUri())
                ?: throw IOException("Nothing to read at $uri")
            Buffer().apply { stream.use { readFrom(it) } }
        }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val authority = data.authority ?: return null
            val own = authority == TV_PROVIDER || authority.startsWith(options.context.packageName)
            return if (data.scheme == "content" && !own) ForeignContentFetcher(data, options, imageLoader) else null
        }
    }

    private companion object {
        const val TV_PROVIDER = "android.media.tv"
    }
}
