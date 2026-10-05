package dev.lumbina.tv.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DitheredGradientTest {

    private val height = 1080
    private val top = Color(0xFF3C1846)
    private val middle = Color(0xFF221028)
    private val bottom = Color(0xFF0E1014)
    private val pixels = ditheredPixels(listOf(top, middle, bottom), height)

    private fun row(y: Int) = pixels.slice(y * STRIP_WIDTH until (y + 1) * STRIP_WIDTH)

    private fun channels(pixel: Int) = listOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)

    @Test
    fun `a row between two shades mixes them instead of picking one`() {
        val mixedRows = (0 until height).count { row(it).distinct().size > 1 }
        assertTrue("only $mixedRows rows of $height are mixed", mixedRows > height * 3 / 4)
    }

    @Test
    fun `neighbouring shades only in every channel, so the noise can't be seen as grain`() {
        for (y in 0 until height step 53) {
            for (channel in 0..2) {
                val shades = row(y).map { channels(it)[channel] }.distinct()
                assertTrue("row $y, channel $channel: $shades", shades.max() - shades.min() <= 1)
            }
        }
    }

    @Test
    fun `the gradient starts at the first color, passes the middle one and ends at the last`() {
        assertEquals(listOf(0x3C, 0x18, 0x46), channels(row(0).min()))
        assertEquals(listOf(0x0E, 0x10, 0x14), channels(row(height - 1).min()))
        val halfway = row(height / 2).map { channels(it)[0] }.distinct()
        assertTrue("red halfway down is $halfway", halfway.all { it in 0x21..0x23 })
    }

    @Test
    fun `no shade outside the two colors a row lies between`() {
        assertTrue(row(height - 1).all { channels(it)[2] <= 0x15 })
        assertTrue(row(0).all { channels(it)[0] <= 0x3D })
    }

    @Test
    fun `the same strip every time, so the background doesn't flicker when drawn again`() {
        assertArrayEquals(pixels, ditheredPixels(listOf(top, middle, bottom), height))
    }
}
