package dev.lumbina.tv.platform.wallpaper

import android.media.MediaFormat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VideoProbeTest {

    @Test
    fun `a video is told by its codec, size and depth`() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 3840, 2160)
        format.setInteger(MediaFormat.KEY_PROFILE, 0x2) // HEVC Main10
        assertEquals("HEVC 3840×2160 10-bit", VideoProbe.describe(format))
        val avc = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
        assertEquals("H.264 1920×1080", VideoProbe.describe(avc))
    }

    @Test
    fun `a file with no picture in it says nothing more`() {
        val junk = File(RuntimeEnvironment.getApplication().cacheDir, "junk.mp4").apply { writeBytes(ByteArray(64)) }
        assertEquals("", VideoProbe.unplayable(junk))
    }
}
