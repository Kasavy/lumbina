package dev.lumbina.tv.platform.accessibility

import dev.lumbina.tv.core.domain.access.AdbState
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedLatelyTest {

    private var time = 0L
    private var asked = 0

    @Test
    fun `the answer is kept a while, then asked again`() {
        var state = AdbState.Connected
        val lately = ConnectedLately({ asked++.let { state } }) { time }
        assertTrue(lately.connected())
        state = AdbState.Off
        time = 10_000
        assertTrue(lately.connected())
        assertEquals(1, asked)
        time = 40_000
        assertFalse(lately.connected())
        assertEquals(2, asked)
    }

    @Test
    fun `a check stuck behind another adb command gives the last answer`() {
        var stuck = false
        val lately = ConnectedLately({
            if (stuck) awaitCancellation()
            AdbState.Connected
        }) { time }
        assertTrue(lately.connected())
        stuck = true
        time = 60_000
        assertTrue(lately.connected())
    }

    @Test
    fun `with nothing known yet, a stuck check says no`() {
        val lately = ConnectedLately({ awaitCancellation() }) { time }
        assertFalse(lately.connected())
    }
}
