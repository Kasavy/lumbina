package dev.lumbina.tv.platform.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class RibbonStartTest {

    @Test
    fun `the selection stays on the cards after one closes from the end`() {
        // Three cards, the last one closed: two left, the selection on the new last card.
        assertEquals(
            RibbonStart(card = 1, onCloseAll = false),
            RibbonStart.of(2, 2, toCloseAll = false, hasCloseAll = true),
        )
        assertEquals(
            RibbonStart(card = 0, onCloseAll = true),
            RibbonStart.of(0, 3, toCloseAll = true, hasCloseAll = true),
        )
        // Down to one app, the ribbon has no "Close all" to stay on.
        assertEquals(
            RibbonStart(card = 0, onCloseAll = false),
            RibbonStart.of(0, 1, toCloseAll = true, hasCloseAll = false),
        )
        assertEquals(
            RibbonStart(card = 0, onCloseAll = false),
            RibbonStart.of(5, 0, toCloseAll = false, hasCloseAll = false),
        )
    }
}
