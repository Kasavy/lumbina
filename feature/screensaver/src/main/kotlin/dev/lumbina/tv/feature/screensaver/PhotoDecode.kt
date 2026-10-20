package dev.lumbina.tv.feature.screensaver

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * A photo decoded just big enough to cover a [width]×[height] screen, cut to what the screen shows
 * of it (the middle, as it is shown cropped), and turned as its camera says (EXIF). Decoding only
 * that part, and that big, keeps a phone's 16 MP portrait photo from taking 64 MB at once.
 * [lean]: two bytes a pixel, for boxes short of memory. Null if it can't be decoded.
 */
internal fun decodeToCover(bytes: ByteArray, width: Int, height: Int, lean: Boolean): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val turn = orientation(bytes)
    val cut = coverCut(bounds.outWidth, bounds.outHeight, width, height, sideways = turn.sideways)
    val options = BitmapFactory.Options().apply {
        inSampleSize = cut.sample
        if (lean) inPreferredConfig = Bitmap.Config.RGB_565
    }
    return decodeRegion(bytes, cut, options)?.let { turnedAndScaled(it, turn, width, height) }
}

/** [part] turned upright and brought down to just cover the screen; itself where nothing needs doing. */
private fun turnedAndScaled(part: Bitmap, turn: Turn, width: Int, height: Int): Bitmap {
    val shown = if (turn.sideways) part.height to part.width else part.width to part.height
    val scale = min(1f, max(width.toFloat() / shown.first, height.toFloat() / shown.second))
    if (turn.degrees == 0 && !turn.mirrored && scale == 1f) return part
    val matrix = Matrix().apply {
        if (turn.mirrored) postScale(-1f, 1f)
        postRotate(turn.degrees.toFloat())
        postScale(scale, scale)
    }
    val turned = Bitmap.createBitmap(part, 0, 0, part.width, part.height, matrix, true)
    if (turned !== part) part.recycle()
    return turned
}

/** The part of a photo to decode, in its stored pixels, and the sample size to decode it at. */
internal data class CoverCut(val left: Int, val top: Int, val right: Int, val bottom: Int, val sample: Int)

/**
 * The middle of a [photoWidth]×[photoHeight] photo that a [width]×[height] screen shows when the
 * photo covers it; [sideways]: the photo is stored turned a quarter (EXIF), its width shown as
 * height. The sample size halves the photo while it still covers the screen.
 */
internal fun coverCut(photoWidth: Int, photoHeight: Int, width: Int, height: Int, sideways: Boolean): CoverCut {
    // As shown: turned upright.
    val (shownW, shownH) = if (sideways) photoHeight to photoWidth else photoWidth to photoHeight
    val cutW = min(shownW.toLong(), shownH.toLong() * width / height).toInt()
    val cutH = min(shownH.toLong(), shownW.toLong() * height / width).toInt()
    var sample = 1
    while (cutW / (sample * 2) >= width && cutH / (sample * 2) >= height) sample *= 2
    // Back to the photo as stored: the middle stays the middle.
    val (storedW, storedH) = if (sideways) cutH to cutW else cutW to cutH
    val left = (photoWidth - storedW) / 2
    val top = (photoHeight - storedH) / 2
    return CoverCut(left, top, left + storedW, top + storedH, sample)
}

private fun decodeRegion(bytes: ByteArray, cut: CoverCut, options: BitmapFactory.Options): Bitmap? = try {
    @Suppress("DEPRECATION") // the replacement needs API 31
    val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
    try {
        decoder.decodeRegion(Rect(cut.left, cut.top, cut.right, cut.bottom), options)
    } finally {
        decoder.recycle()
    }
} catch (_: IOException) {
    // A format the region decoder doesn't take: the whole photo, cropped as it shows.
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private class Turn(val degrees: Int, val mirrored: Boolean) {
    val sideways: Boolean get() = degrees % HALF_TURN != 0
}

private fun orientation(bytes: ByteArray): Turn {
    val normal = ExifInterface.ORIENTATION_NORMAL
    val tag = runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, normal) }
        .getOrDefault(normal)
    return when (tag) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Turn(0, mirrored = true)
        ExifInterface.ORIENTATION_ROTATE_180 -> Turn(HALF_TURN, mirrored = false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Turn(HALF_TURN, mirrored = true)
        ExifInterface.ORIENTATION_TRANSPOSE -> Turn(QUARTER_TURN, mirrored = true)
        ExifInterface.ORIENTATION_ROTATE_90 -> Turn(QUARTER_TURN, mirrored = false)
        ExifInterface.ORIENTATION_TRANSVERSE -> Turn(THREE_QUARTERS, mirrored = true)
        ExifInterface.ORIENTATION_ROTATE_270 -> Turn(THREE_QUARTERS, mirrored = false)
        else -> Turn(0, mirrored = false)
    }
}

private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val THREE_QUARTERS = 270
