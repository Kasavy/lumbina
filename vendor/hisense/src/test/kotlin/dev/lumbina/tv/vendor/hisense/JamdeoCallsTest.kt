package dev.lumbina.tv.vendor.hisense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamdeoCallsTest {

    @Test
    fun `numbers read from the firmware are taken as they are, a C2U's shifted ones too`() {
        val shifted = mapOf(
            SOURCE_SERVICE_STUB to "setCurrentInputSource" to 2,
            SOURCE_SERVICE_STUB to "getCurrentInputSource" to 5,
            SOURCE_SERVICE_STUB to "getInputSourceName" to 12,
            SOURCE_SERVICE_STUB to "getInputSourceArray" to 11,
            SOURCE_SERVICE_STUB to "getConectedSourcesArray" to 38,
            SYSTEM_SERVICE_STUB to "invokeTvMethod" to 60,
        )
        val calls = jamdeoCalls { service, method -> shifted[service to method] }
        assertEquals(JamdeoCalls(2, 5, 12, 11, 38, 60, fromFirmware = true), calls)
    }

    @Test
    fun `a number that can't be read is the C3 Ultra's, and the report says so`() {
        val calls = jamdeoCalls { _, method -> 99.takeIf { method == "getInputSourceName" } }
        assertEquals(JamdeoCalls(1, 4, 99, 12, 37, 57, fromFirmware = false), calls)
        assertTrue(calls.said().endsWith("(some as on a C3 Ultra)"))
        assertFalse(jamdeoCalls { _, _ -> null }.fromFirmware)
    }
}
