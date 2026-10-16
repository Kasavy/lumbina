package dev.lumbina.tv.feature.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FailuresTest {

    @Test
    fun `a failure is said for a few seconds and then let go`() = runTest {
        val failures = Failures(backgroundScope)
        failures.say(Failed.App("YouTube"))
        runCurrent()
        assertEquals(Failed.App("YouTube"), failures.state.value)
        advanceTimeBy(7_000)
        assertNull(failures.state.value)
    }

    @Test
    fun `a new failure replaces the one before and gets its full time`() = runTest {
        val failures = Failures(backgroundScope)
        failures.say(Failed.App("YouTube"))
        advanceTimeBy(5_000)
        failures.say(Failed.Program("Films"))
        advanceTimeBy(5_000)
        assertEquals(Failed.Program("Films"), failures.state.value)
        advanceTimeBy(2_000)
        assertNull(failures.state.value)
    }
}
