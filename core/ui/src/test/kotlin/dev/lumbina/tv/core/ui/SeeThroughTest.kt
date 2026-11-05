package dev.lumbina.tv.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeeThroughTest {
    private val opaque = 0xFF202020.toInt()
    private val clear = 0x00000000

    @Test
    fun `opaque all over is not see-through, whatever the bitmap says it could be`() =
        assertFalse(seeThrough(IntArray(1000) { opaque }))

    @Test
    fun `a stray clear pixel or two along an edge doesn't count`() =
        assertFalse(seeThrough(IntArray(1000) { if (it < 2) clear else opaque }))

    @Test
    fun `clear corners or a clear ground do`() {
        assertTrue(seeThrough(IntArray(1000) { if (it < 20) clear else opaque }))
        assertTrue(seeThrough(IntArray(1000) { if (it % 2 == 0) clear else opaque }))
    }

    @Test
    fun `half see-through is see-through, nearly opaque is not`() {
        assertTrue(seeThrough(IntArray(100) { 0x40FFFFFF }))
        assertFalse(seeThrough(IntArray(100) { 0xF0FFFFFF.toInt() }))
    }
}
