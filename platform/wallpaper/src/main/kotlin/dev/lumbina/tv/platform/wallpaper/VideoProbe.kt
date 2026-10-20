package dev.lumbina.tv.platform.wallpaper

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * Whether the device can play a video: its picture track, and a decoder for it among the
 * device's own (the format with its size and profile, as the player asks for it). A sent video
 * the device can't show would only be a black wallpaper.
 */
internal object VideoProbe {

    /**
     * Null when the device can play [file]; otherwise what the video is, to say why not, or "" when
     * there is no picture to read in it (a damaged file, sound only).
     */
    fun unplayable(file: File): String? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val video = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }
                ?: return NO_PICTURE
            // The frame rate is not what a decoder is chosen by, and some devices refuse a format with it.
            video.setString(MediaFormat.KEY_FRAME_RATE, null)
            val decoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(video)
            return if (decoder == null) describe(video) else null
        } catch (_: java.io.IOException) {
            return NO_PICTURE
        } catch (_: IllegalArgumentException) {
            return NO_PICTURE
        } finally {
            extractor.release()
        }
    }

    /** E.g. "HEVC 3840×2160 10-bit": what one would look for in a video's properties. */
    internal fun describe(format: MediaFormat): String {
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val codec = CODECS[mime] ?: mime.removePrefix("video/").uppercase()
        val size = if (format.containsKey(MediaFormat.KEY_WIDTH) && format.containsKey(MediaFormat.KEY_HEIGHT)) {
            "${format.getInteger(MediaFormat.KEY_WIDTH)}×${format.getInteger(MediaFormat.KEY_HEIGHT)}"
        } else {
            null
        }
        val profile = if (format.containsKey(
                MediaFormat.KEY_PROFILE,
            )
        ) {
            format.getInteger(MediaFormat.KEY_PROFILE)
        } else {
            -1
        }
        val deep = profile in TEN_BIT_PROFILES[mime].orEmpty()
        return listOfNotNull(codec, size, "10-bit".takeIf { deep }).joinToString(" ")
    }

    private const val NO_PICTURE = ""

    // Literals for those newer than Android 8: the same values the constants have.
    private const val AV1 = "video/av01"

    private val CODECS = mapOf(
        MediaFormat.MIMETYPE_VIDEO_AVC to "H.264",
        MediaFormat.MIMETYPE_VIDEO_HEVC to "HEVC",
        MediaFormat.MIMETYPE_VIDEO_VP8 to "VP8",
        MediaFormat.MIMETYPE_VIDEO_VP9 to "VP9",
        AV1 to "AV1",
        MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION to "Dolby Vision",
        MediaFormat.MIMETYPE_VIDEO_MPEG4 to "MPEG-4",
    )

    /** Per codec, as profile numbers mean different things in each: the profiles of 10-bit video. */
    private val TEN_BIT_PROFILES = mapOf(
        MediaFormat.MIMETYPE_VIDEO_AVC to setOf(CodecProfileLevel.AVCProfileHigh10),
        // Main10, Main10HDR10, Main10HDR10Plus.
        MediaFormat.MIMETYPE_VIDEO_HEVC to setOf(0x2, 0x1000, 0x2000),
        // Profile2, Profile2HDR, Profile2HDR10Plus, and the same for Profile3.
        MediaFormat.MIMETYPE_VIDEO_VP9 to setOf(0x4, 0x8, 0x1000, 0x2000, 0x4000, 0x8000),
        // Main10, Main10HDR10, Main10HDR10Plus.
        AV1 to setOf(0x2, 0x1000, 0x2000),
    )
}
