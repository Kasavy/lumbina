package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosedByKeyTest {

    private var now = 0L
    private val closed = ClosedByKey { now }

    @Test
    fun `the closing press's own binding opens nothing`() {
        closed.closedBy(MENU)
        now = 300
        closed.seen(MENU, KeyEvent.ACTION_UP, 0)
        assertTrue(closed.consume())
    }

    @Test
    fun `another key pressed after closing it, the menu's button opens it`() {
        closed.closedBy(VOLUME)
        closed.seen(VOLUME, KeyEvent.ACTION_UP, 0)
        now = 200
        closed.seen(MENU, KeyEvent.ACTION_DOWN, 0)
        closed.seen(MENU, KeyEvent.ACTION_UP, 0)
        assertFalse(closed.consume())
    }

    @Test
    fun `a slow press, the wait counts from the release`() {
        closed.closedBy(MENU)
        now = 1_500
        closed.seen(MENU, KeyEvent.ACTION_UP, 0)
        now = 1_800
        assertTrue(closed.consume())
    }

    @Test
    fun `a double press closing the ribbon, its second press goes on with the gesture`() {
        closed.closedBy(HOME)
        now = 100
        closed.seen(HOME, KeyEvent.ACTION_UP, 0)
        now = 250
        closed.seen(HOME, KeyEvent.ACTION_DOWN, 0)
        assertTrue(closed.consume())
    }

    @Test
    fun `long after, and only once`() {
        closed.closedBy(MENU)
        now = 1_000
        assertFalse(closed.consume())
        closed.closedBy(MENU)
        assertTrue(closed.consume())
        assertFalse(closed.consume())
    }

    private companion object {
        const val MENU = KeyEvent.KEYCODE_MENU
        const val HOME = KeyEvent.KEYCODE_HOME
        const val VOLUME = KeyEvent.KEYCODE_VOLUME_UP
    }
}
