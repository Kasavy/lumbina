package dev.lumbina.tv.platform.accessibility

import dev.lumbina.tv.core.domain.backdrop.MediaAdd
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UploadAnswerTest {

    private fun answer(added: MediaAdd) = JSONObject(uploadAnswer(added).json)

    @Test
    fun `each refusal is told with the reason the page words, a video with what it is`() {
        assertEquals("format", answer(MediaAdd.NotMedia).getString("error"))
        assertEquals("room", answer(MediaAdd.NoRoom).getString("error"))
        val video = answer(MediaAdd.Unplayable("HEVC 3840×2160 10-bit"))
        assertEquals("video", video.getString("error"))
        assertEquals("HEVC 3840×2160 10-bit", video.getString("detail"))
    }

    @Test
    fun `a file cut short is a plain failure, with no reason to word`() {
        val cut = answer(MediaAdd.Incomplete)
        assertFalse(cut.getBoolean("ok"))
        assertFalse(cut.has("error"))
    }
}
