package dev.lumbina.tv.core.ui

import dev.lumbina.tv.core.ui.HeldStepPacer.Companion.STEP_MILLIS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldStepPacerTest {

    private val pacer = HeldStepPacer()

    @Test
    fun `a press of its own always goes through`() {
        assertTrue(pacer.passes(repeatCount = 0, eventTime = 1_000))
        assertTrue(pacer.passes(repeatCount = 0, eventTime = 1_010))
    }

    @Test
    fun `a repeat sooner than a step after the last is dropped`() {
        pacer.passes(repeatCount = 0, eventTime = 1_000)
        assertFalse(pacer.passes(repeatCount = 1, eventTime = 1_000 + STEP_MILLIS - 1))
    }

    @Test
    fun `a repeat a step after the last goes through`() {
        pacer.passes(repeatCount = 0, eventTime = 1_000)
        assertTrue(pacer.passes(repeatCount = 1, eventTime = 1_000 + STEP_MILLIS))
    }

    @Test
    fun `a dropped repeat does not put off the next step`() {
        pacer.passes(repeatCount = 0, eventTime = 1_000)
        assertFalse(pacer.passes(repeatCount = 1, eventTime = 1_050))
        assertTrue(pacer.passes(repeatCount = 2, eventTime = 1_100))
    }

    @Test
    fun `repeats every 50 ms go through every second one`() {
        pacer.passes(repeatCount = 0, eventTime = 0)
        val passed = (1..10).map { pacer.passes(repeatCount = it, eventTime = it * 50L) }
        assertTrue(passed == List(10) { it % 2 == 1 })
    }
}
