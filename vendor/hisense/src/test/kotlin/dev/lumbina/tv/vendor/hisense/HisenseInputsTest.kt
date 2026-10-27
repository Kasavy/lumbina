package dev.lumbina.tv.vendor.hisense

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
}
