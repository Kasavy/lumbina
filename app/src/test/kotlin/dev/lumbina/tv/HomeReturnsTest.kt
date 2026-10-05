package dev.lumbina.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReturnsTest {

    private val returns = HomeReturns(longAbsenceMillis = HOUR)

    /** Lumbina goes off screen at [at] and comes back [after] later, by a new intent. */
    private fun comeBack(at: Long = 0, after: Long = MINUTE, toStartAsked: Boolean = false): NavRequest {
        returns.stopped(at)
        val started = returns.started(at + after)
        return returns.newIntent(settingsPage = null, toStartAsked = toStartAsked).also {
            if (started != null) assertTrue(started.toStart)
            returns.resumed()
        }
    }

    @Test
    fun `a first start asks for nothing but a settings page`() {
        assertNull(returns.created(restored = false, freshProcess = true, settingsPage = null))
        val settings = returns.created(restored = false, freshProcess = true, settingsPage = "Rows")
        assertEquals("Rows", settings?.settingsPage)
    }

    @Test
    fun `restored in a new process, home starts over`() {
        assertTrue(returns.created(restored = true, freshProcess = true, settingsPage = null)!!.toStart)
    }

    @Test
    fun `re-created in the same process, the open screen stays`() {
        assertNull(returns.created(restored = true, freshProcess = false, settingsPage = null))
    }

    @Test
    fun `home from an app goes back to the tile it was left on`() {
        val request = comeBack()
        assertFalse(request.sendsHomeToStart(onHome = true))
    }

    @Test
    fun `home pressed on home goes to the first tile, from settings back to where it was`() {
        val request = returns.newIntent(settingsPage = null, toStartAsked = false)
        assertTrue(request.sendsHomeToStart(onHome = true))
        assertFalse(request.sendsHomeToStart(onHome = false))
    }

    @Test
    fun `after a long absence home starts over`() {
        returns.stopped(0)
        assertTrue(returns.started(HOUR + 1)!!.toStart)
        assertTrue(returns.newIntent(settingsPage = null, toStartAsked = false).toStart)
    }

    @Test
    fun `starting without having left asks for nothing`() {
        assertNull(returns.started(5 * HOUR))
    }

    @Test
    fun `a short absence keeps the tile`() {
        assertFalse(comeBack(after = MINUTE).toStart)
    }

    @Test
    fun `resuming forgets the absence`() {
        comeBack(after = HOUR + 1)
        val request = returns.newIntent(settingsPage = null, toStartAsked = false)
        assertTrue(request.onScreen)
        assertFalse(request.toStart)
    }

    @Test
    fun `power-on asks for the first tile`() {
        assertTrue(comeBack(toStartAsked = true).sendsHomeToStart(onHome = true))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
