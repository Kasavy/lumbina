package dev.lumbina.tv.feature.screensaver

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverCutTest {

    @Test
    fun `a portrait photo, the middle band the screen shows, whole width, at full size`() {
        // 3464×4618 on 1920×1080: the band is 3464×1948, too small to halve and still cover.
        assertEquals(CoverCut(0, 1335, 3464, 3283, sample = 1), coverCut(3464, 4618, 1920, 1080, sideways = false))
    }

    @Test
    fun `a big landscape photo is halved while it still covers the screen`() {
        assertEquals(CoverCut(0, 0, 8000, 4500, sample = 4), coverCut(8000, 4500, 1920, 1080, sideways = false))
    }

    @Test
    fun `stored sideways, the cut is taken across, upright it is the band the screen shows`() {
        // Stored 4618×3464, shown upright 3464×4618: the stored cut is 1948 wide, all 3464 high.
        assertEquals(CoverCut(1335, 0, 3283, 3464, sample = 1), coverCut(4618, 3464, 1920, 1080, sideways = true))
    }
}
