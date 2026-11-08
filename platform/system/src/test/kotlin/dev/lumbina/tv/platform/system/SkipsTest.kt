package dev.lumbina.tv.platform.system

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class SkipsTest {

    @Test
    fun `a player that names no actions keeps both buttons`() {
        assertEquals(Skips(previous = true, next = true), Skips.of(null))
        assertEquals(Skips(previous = true, next = true), Skips.of(0L))
    }

    @Test
    fun `a live stream that only plays and pauses has no skip buttons`() {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE
        assertEquals(Skips(previous = false, next = false), Skips.of(actions))
    }

    @Test
    fun `the last item of a list keeps previous only`() {
        val actions = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        assertEquals(Skips(previous = true, next = false), Skips.of(actions))
    }
}
