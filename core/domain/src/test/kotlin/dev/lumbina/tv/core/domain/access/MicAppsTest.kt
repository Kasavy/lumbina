package dev.lumbina.tv.core.domain.access

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MicAppsTest {

    private val settings = AccessSettings().withMicApp(
        "tv.cinema",
        allowed = true,
    ).withMicApp("tv.radio", allowed = false)

    @Test
    fun `only the apps answered yes hear the remote`() {
        assertEquals(setOf("tv.cinema"), settings.micAllowed)
        assertTrue(AccessSettings().micAllowed.isEmpty())
    }

    @Test
    fun `an app on screen is asked about once, whatever the answer was`() {
        assertTrue(settings.asksAboutMic("tv.news", inFront = "tv.news"))
        assertFalse(settings.asksAboutMic("tv.cinema", inFront = "tv.cinema"))
        assertFalse(settings.asksAboutMic("tv.radio", inFront = "tv.radio"))
    }

    @Test
    fun `an app recording behind another one is not asked about`() {
        assertFalse(settings.asksAboutMic("tv.news", inFront = "tv.cinema"))
        assertFalse(settings.asksAboutMic("tv.news", inFront = null))
    }

    @Test
    fun `a forgotten answer is asked for again`() {
        val forgotten = settings.withoutMicApp("tv.cinema")
        assertTrue(forgotten.micAllowed.isEmpty())
        assertTrue(forgotten.asksAboutMic("tv.cinema", inFront = "tv.cinema"))
        assertFalse(forgotten.asksAboutMic("tv.radio", inFront = "tv.radio"))
    }

    @Test
    fun `an answer can be changed`() {
        assertEquals(setOf("tv.cinema", "tv.radio"), settings.withMicApp("tv.radio", allowed = true).micAllowed)
        assertTrue(settings.withMicApp("tv.cinema", allowed = false).micAllowed.isEmpty())
    }
}
