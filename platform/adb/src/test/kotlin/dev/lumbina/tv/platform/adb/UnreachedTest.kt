package dev.lumbina.tv.platform.adb

import dev.lumbina.tv.core.domain.access.AdbState
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreachedTest {

    @Test
    fun `with debugging off that is the reason, whatever happened`() {
        assertEquals(AdbState.Off, unreached(refused = true, debuggingOn = false))
        assertEquals(AdbState.Off, unreached(refused = false, debuggingOn = false))
    }

    @Test
    fun `refused with debugging on, the device lets only a computer in`() {
        assertEquals(AdbState.NotListening, unreached(refused = true, debuggingOn = true))
    }

    @Test
    fun `dropped with debugging on is a failure, not advice to turn it on`() {
        assertEquals(AdbState.Failed, unreached(refused = false, debuggingOn = true))
    }
}
