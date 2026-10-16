package dev.lumbina.tv.core.ui

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.FLAG_LONG_PRESS
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import androidx.compose.ui.input.key.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OkPressTest {

    private val ok = OkPress()
    private var clicks = 0
    private var longClicks = 0

    private fun key(action: Int, repeat: Int = 0, flags: Int = 0, code: Int = KEYCODE_DPAD_CENTER) =
        KeyEvent(android.view.KeyEvent(0, 0, action, code, repeat, 0, 0, 0, flags))

    private fun send(event: KeyEvent, long: Boolean = true) =
        ok.handle(event, onClick = { clicks++ }, onLongClick = if (long) ({ longClicks++ }) else null)

    @Test
    fun `a short press clicks when released and shows as pressed till then`() {
        send(key(ACTION_DOWN))
        assertTrue(ok.pressed)
        assertEquals(0, clicks)
        send(key(ACTION_UP))
        assertEquals(1, clicks)
        assertFalse(ok.pressed)
    }

    @Test
    fun `a long press long-clicks once and does not click`() {
        send(key(ACTION_DOWN))
        send(key(ACTION_DOWN, repeat = 1, flags = FLAG_LONG_PRESS))
        send(key(ACTION_DOWN, repeat = 2))
        assertFalse(ok.pressed)
        send(key(ACTION_UP))
        assertEquals(1, longClicks)
        assertEquals(0, clicks)
    }

    @Test
    fun `held without a long click it is a click`() {
        send(key(ACTION_DOWN), long = false)
        send(key(ACTION_DOWN, repeat = 1, flags = FLAG_LONG_PRESS), long = false)
        send(key(ACTION_UP), long = false)
        assertEquals(1, clicks)
    }

    @Test
    fun `OK held while the focus arrived is no click`() {
        send(key(ACTION_DOWN, repeat = 5))
        send(key(ACTION_UP))
        assertEquals(0, clicks)
    }

    @Test
    fun `a press forgotten as the focus left is no click`() {
        send(key(ACTION_DOWN))
        ok.cancel()
        assertFalse(ok.pressed)
        send(key(ACTION_UP))
        assertEquals(0, clicks)
    }

    @Test
    fun `other keys are left alone`() {
        assertFalse(send(key(ACTION_DOWN, code = KEYCODE_DPAD_DOWN)))
    }
}
