package dev.lumbina.tv.platform.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckDueTest {
    private val gap = 300_000L

    @Test
    fun `due when none was made`() = assertTrue(checkDue(LastCheck(0, NO_BOOT), LastCheck(60_000, 7), gap))

    @Test
    fun `not due within the gap in the same boot`() =
        assertFalse(checkDue(LastCheck(90_000, 7), LastCheck(200_000, 7), gap))

    @Test
    fun `due once the gap has passed`() = assertTrue(checkDue(LastCheck(90_000, 7), LastCheck(390_000, 7), gap))

    @Test
    fun `due in another boot, though the times since boot are close`() =
        assertTrue(checkDue(LastCheck(90_000, 7), LastCheck(200_000, 8), gap))

    @Test
    fun `due in another boot that has run for less than the last check had`() =
        assertTrue(checkDue(LastCheck(900_000, 7), LastCheck(60_000, 8), gap))

    @Test
    fun `by the time alone where a boot is not known`() {
        assertFalse(checkDue(LastCheck(90_000, NO_BOOT), LastCheck(200_000, 8), gap))
        assertTrue(checkDue(LastCheck(900_000, 7), LastCheck(60_000, NO_BOOT), gap))
    }
}
