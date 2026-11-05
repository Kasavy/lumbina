package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyEchoTest {

    private val echoes = mutableListOf<KeyStamp>()
    private val echo = KeyEcho(onEcho = { echoes += it }, remembered = 3)
    private var handled = 0

    private fun stamp(action: Int, at: Long, repeat: Int = 0) = KeyStamp(
        deviceId = 7,
        keyCode = 3,
        scanCode = 102,
        action = action,
        repeatCount = repeat,
        downTime = 100,
        eventTime = at,
    )

    private fun deliver(stamp: KeyStamp, answer: Boolean = true) = echo.answer(stamp) {
        handled++
        answer
    }

    @Test
    fun `a press handed over twice acts once and answers the same both times`() {
        val down = stamp(KeyEvent.ACTION_DOWN, at = 100)
        assertTrue(deliver(down))
        assertTrue(deliver(down, answer = false))
        assertEquals(1, handled)
        assertEquals(listOf(down), echoes)
    }

    @Test
    fun `a repeat and the release are new events`() {
        deliver(stamp(KeyEvent.ACTION_DOWN, at = 100))
        deliver(stamp(KeyEvent.ACTION_DOWN, at = 600, repeat = 1))
        assertFalse(deliver(stamp(KeyEvent.ACTION_UP, at = 700), answer = false))
        assertEquals(3, handled)
        assertTrue(echoes.isEmpty())
    }

    @Test
    fun `only the last few are remembered, and the log hears of the first echo only`() {
        val first = stamp(KeyEvent.ACTION_DOWN, at = 1)
        deliver(first)
        deliver(first)
        (2L..4L).forEach { deliver(stamp(KeyEvent.ACTION_DOWN, at = it)) }
        deliver(stamp(KeyEvent.ACTION_DOWN, at = 4))
        deliver(first)
        assertEquals(5, handled)
        assertEquals(listOf(first), echoes)
    }
}
