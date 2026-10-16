package dev.lumbina.tv.core.domain.sleep

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SleepTimerTest {

    @Test
    fun `from off the shortest timer comes first`() {
        assertEquals(15, nextSleepTimer(null))
    }

    @Test
    fun `a running timer steps to the next longer length`() {
        assertEquals(30, nextSleepTimer(15))
        assertEquals(60, nextSleepTimer(42))
    }

    @Test
    fun `after the longest the timer goes off`() {
        assertNull(nextSleepTimer(120))
    }

    @Test
    fun `a step back takes the next shorter length, and off before the shortest`() {
        assertEquals(15, previousSleepTimer(25))
        assertEquals(90, previousSleepTimer(120))
        assertNull(previousSleepTimer(15))
        assertNull(previousSleepTimer(7))
    }
}
