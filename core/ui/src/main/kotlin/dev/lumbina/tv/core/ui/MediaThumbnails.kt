package dev.lumbina.tv.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import java.io.File
import java.io.IOException
import okio.Buffer

/**
 * A small picture of a photo or video of the user's, for a tile: [uri] a file of Lumbina's own,
 * a video shown by its frame at [videoFrameMillis] (null for a photo). Made once, at the size of a
 * tile, and kept in the image loader's disk cache: a photo of several megapixels and a video's
 * frame cost a weak box a while each, and a picker that made them all again at every start of
 * Lumbina opened in jerks. On disk, a changed file (another size or time) gets a new one.
 */
data class MediaThumbnail(val uri: String, val videoFrameMillis: Long? = null)

internal class MediaThumbnailFetcher(
    private val data: MediaThumbnail,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val file = data.uri.toUri().path?.let(::File)?.takeIf { it.isFile }
            ?: throw IOException("No file at ${data.uri}")
        return cachedOrLoad(imageLoader, options, file.cacheKey(), DataSource.DISK) {
            val picture = (if (data.videoFrameMillis != null) frame(file, data.videoFrameMillis) else photo(file))
                ?: throw IOException("Nothing to show of $file")
            Buffer().apply {
                // A see-through banner keeps its see-through parts: JPEG would make them black.
                val format = if (picture.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                picture.compress(format, QUALITY, outputStream())
                picture.recycle()
            }
        }
    }

    // The version first: pictures made the older way (black see-through parts, upright ones soft) are made again.
    private fun File.cacheKey() =
        "thumbnail$VERSION:$THUMBNAIL_WIDTH:${data.videoFrameMillis}:$path:${length()}:${lastModified()}"

    class Factory : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options, imageLoader)
    }

    /**
     * Kept in memory by the file's address alone: a file replaced under the same name would show its
     * old picture until Lumbina restarts. None is: a file's name starts with the time it came.
     */
    class Key : Keyer<MediaThumbnail> {
        override fun key(data: MediaThumbnail, options: Options): String =
            "thumbnail:${data.uri}:${data.videoFrameMillis}"
    }
}

/** The photo read at about [THUMBNAIL_WIDTH], turned as the camera says it was held. */
private fun photo(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    val orientation = runCatching {
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
        ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
        ExifInterface.ORIENTATION_ROTATE_270 -> HALF_TURN + QUARTER_TURN
        else -> 0
    }
    // Read by the width it will have once turned: a photo held upright is as wide as its height.
    val width = if (degrees % HALF_TURN == 0) bounds.outWidth else bounds.outHeight
    var sample = 1
    while (width / (sample * 2) >= THUMBNAIL_WIDTH) sample *= 2
    val read = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    return fitted(read, degrees)
}

/** The video's frame near [millis], at about [THUMBNAIL_WIDTH]. */
private fun frame(file: File, millis: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.path)
        val micros = millis * MICROS_IN_MILLI
        val option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val shot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Fitted into a box as wide as a tile and far taller: an upright video keeps the width too.
            retriever.getScaledFrameAtTime(micros, option, THUMBNAIL_WIDTH, THUMBNAIL_WIDTH * TALL)
        } else {
            retriever.getFrameAtTime(micros, option)
        }
        shot?.let { fitted(it, degrees = 0) }
    } catch (_: RuntimeException) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** [bitmap] turned by [degrees] and brought down to [THUMBNAIL_WIDTH] wide, if it is wider. */
private fun fitted(bitmap: Bitmap, degrees: Int): Bitmap {
    val turned = degrees % FULL_TURN != 0
    val width = if (degrees % HALF_TURN == 0) bitmap.width else bitmap.height
    val scale = (THUMBNAIL_WIDTH.toFloat() / width).coerceAtMost(1f)
    if (!turned && scale == 1f) return bitmap
    val matrix = Matrix().apply {
        postRotate(degrees.toFloat())
        postScale(scale, scale)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}

/** As wide as a tile is drawn, with room for its growth on focus. */
private const val THUMBNAIL_WIDTH = 480
private const val QUALITY = 85
private const val TALL = 4
private const val VERSION = 2
private const val MICROS_IN_MILLI = 1_000L
private const val FULL_TURN = 360
private const val HALF_TURN = 180
private const val QUARTER_TURN = 90
