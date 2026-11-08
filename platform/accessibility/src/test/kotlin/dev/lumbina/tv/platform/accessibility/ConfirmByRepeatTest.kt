package dev.lumbina.tv.platform.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmByRepeatTest {

    private var now = 0L
    private val confirm = ConfirmByRepeat(windowMillis = 4_000L) { now }

    @Test
    fun `the first press asks, the same again soon after the question confirms`() {
        assertFalse(confirm.confirms())
        now = 1_000L
        confirm.asked()
        now = 4_500L
        assertTrue(confirm.confirms())
        // Used up: the next press asks again.
        assertFalse(confirm.confirms())
    }

    @Test
    fun `the window runs from the question, not from the press`() {
        assertFalse(confirm.confirms())
        // The question came a while after the press (the device was asked first).
        now = 3_000L
        confirm.asked()
        now = 6_500L
        assertTrue(confirm.confirms())
    }

    @Test
    fun `too late, or a question forgotten, asks again`() {
        confirm.asked()
        now = 4_001L
        assertFalse(confirm.confirms())
        confirm.asked()
        confirm.forget()
        assertFalse(confirm.confirms())
    }
}
