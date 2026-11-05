package dev.lumbina.tv.vendor.hisense

import dev.lumbina.tv.core.domain.device.InputPresence
import org.junit.Assert.assertEquals
import org.junit.Test

class HisenseInputsTest {

    @Test
    fun `an input's number stands apart from its name, once`() {
        assertEquals("HDMI 1", spacedName("HDMI1"))
        assertEquals("HDMI 1", spacedName("HDMI 1"))
        assertEquals("AV 12", spacedName("AV12"))
        assertEquals("1281", spacedName("1281"))
        assertEquals("Приставка", spacedName("Приставка"))
    }

    @Test
    fun `a reply for the report says what was read and every word as written`() {
        assertEquals("\"0\" · 12 bytes: 00000000 00000001 00000030", describeReply("0", 12, listOf(0, 1, 0x30)))
        assertEquals("null · 4 bytes: ffffffff", describeReply(null, 4, listOf(-1)))
    }

    @Test
    fun `a device that sends its name or kind works, a cable alone is connected, else empty`() {
        assertEquals(InputPresence.Working("MIBOX4"), presenceOf(cabled = true, name = "MIBOX4", kind = 14))
        assertEquals(InputPresence.Working(null), presenceOf(cabled = true, name = "", kind = 14))
        assertEquals(InputPresence.Connected, presenceOf(cabled = true, name = "", kind = 0))
        assertEquals(InputPresence.Empty, presenceOf(cabled = false, name = "", kind = 0))
        assertEquals(InputPresence.Connected, presenceOf(cabled = true, name = null, kind = null))
        // The cable call not answered: a sending device is still told, nothing else is.
        assertEquals(InputPresence.Working("MIBOX4"), presenceOf(cabled = null, name = "MIBOX4", kind = 14))
        assertEquals(null, presenceOf(cabled = null, name = "", kind = 0))
    }
}
