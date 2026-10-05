package dev.lumbina.tv.feature.screensaver

import org.junit.Assert.assertEquals
import org.junit.Test

class CornerTest {

    private val minute = 60_000L

    @Test
    fun `the first corner holds for five minutes from the start, whatever the clock says`() {
        val start = 12 * 60 * minute + 4 * minute + 30_000 // 12:04:30, half a minute before a 5-minute mark
        assertEquals(0, cornerIndex(start + minute, start, 4)) // 12:05:30, past the wall clock's mark
        assertEquals(0, cornerIndex(start + 4 * minute, start, 4))
        assertEquals(1, cornerIndex(start + 5 * minute, start, 4))
        assertEquals(0, cornerIndex(start + 20 * minute, start, 4))
    }
}
