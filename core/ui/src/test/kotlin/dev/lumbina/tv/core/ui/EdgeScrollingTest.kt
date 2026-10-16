package dev.lumbina.tv.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeScrollingTest {

    private val spec = EdgeSpec(margin = 50f)

    @Test
    fun `a tile already in view does not move the row`() {
        assertEquals(0f, spec.calculateScrollDistance(offset = 600f, size = 200f, containerSize = 1000f))
        assertEquals(0f, spec.calculateScrollDistance(offset = 50f, size = 200f, containerSize = 1000f))
    }

    @Test
    fun `a tile past an edge comes in just far enough to show whole`() {
        assertEquals(50f, spec.calculateScrollDistance(offset = 800f, size = 200f, containerSize = 1000f))
        assertEquals(-80f, spec.calculateScrollDistance(offset = -30f, size = 200f, containerSize = 1000f))
    }

    @Test
    fun `a tile wider than the room is lined up with the start`() {
        assertEquals(150f, spec.calculateScrollDistance(offset = 200f, size = 950f, containerSize = 1000f))
    }
}
