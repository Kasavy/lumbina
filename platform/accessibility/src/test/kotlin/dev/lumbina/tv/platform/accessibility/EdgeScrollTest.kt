package dev.lumbina.tv.platform.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeScrollTest {

    // A 1000 px wide ribbon, the selection kept 50 px from its sides, cards 200 px apart.
    private fun scroll(left: Int, scrollX: Int) = edgeScroll(left, left + 200, scrollX, width = 1000, margin = 50)

    @Test
    fun `a card well inside leaves the ribbon where it is`() {
        assertNull(scroll(left = 50, scrollX = 0))
        assertNull(scroll(left = 650, scrollX = 0))
        // Right at the margin, on either side.
        assertNull(scroll(left = 750, scrollX = 0))
        assertNull(scroll(left = 150, scrollX = 100))
    }

    @Test
    fun `a card past the right edge scrolls just enough to show it with the margin`() {
        assertEquals(100, scroll(left = 850, scrollX = 0))
        assertEquals(300, scroll(left = 1050, scrollX = 100))
    }

    @Test
    fun `a card past the left edge scrolls back as far, never before the start`() {
        assertEquals(200, scroll(left = 250, scrollX = 300))
        assertEquals(0, scroll(left = 30, scrollX = 100))
    }
}
