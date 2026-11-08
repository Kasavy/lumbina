package dev.lumbina.tv.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProcessNameTest {

    @Test
    fun `the name is the command line's first argument`() {
        assertEquals(
            "dev.lumbina.tv:ui",
            processNameOf("dev.lumbina.tv:ui\u0000".toByteArray()),
        )
        assertEquals("app_process", processNameOf("app_process\u0000/system/bin\u0000Main\u0000".toByteArray()))
    }

    @Test
    fun `an empty command line gives no name`() {
        assertNull(processNameOf(ByteArray(0)))
        assertNull(processNameOf("\u0000".toByteArray()))
    }
}
