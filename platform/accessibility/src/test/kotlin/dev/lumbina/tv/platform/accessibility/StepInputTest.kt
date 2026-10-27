package dev.lumbina.tv.platform.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepInputTest {

    private val inputs = listOf("1281", "1282")

    @Test
    fun `the arrows go from Android's picture through the inputs and stop at either end`() {
        assertEquals("1281", stepInput(inputs, null, 1))
        assertEquals("1282", stepInput(inputs, "1281", 1))
        assertEquals("1282", stepInput(inputs, "1282", 1))
        assertEquals("1281", stepInput(inputs, "1282", -1))
        assertNull(stepInput(inputs, "1281", -1))
        assertNull(stepInput(inputs, null, -1))
    }

    @Test
    fun `an input no longer listed counts as Android's picture`() {
        assertEquals("1281", stepInput(inputs, "2561", 1))
    }
}
