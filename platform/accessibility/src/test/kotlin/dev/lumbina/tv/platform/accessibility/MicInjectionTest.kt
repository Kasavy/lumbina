package dev.lumbina.tv.platform.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicInjectionTest {

    @Test
    fun `the helper's lines are read`() {
        assertEquals(HelperLine.Log("feeding 10089"), HelperLine.of("log feeding 10089"))
        assertEquals(HelperLine.Recording(10089, on = true), HelperLine.of("rec 10089 1"))
        assertEquals(HelperLine.Recording(10089, on = false), HelperLine.of("rec 10089 0"))
        assertEquals(HelperLine.Heard(10090), HelperLine.of("heard 10090"))
    }

    @Test
    fun `the pump sends the remote's sound only to an app with the user's leave that records by itself`() {
        val cinema = HelperLine.Recording(10090, on = true)
        val search = HelperLine.Recording(10091, on = true)
        val fed = MicInjection.Fed(
            apps = mapOf(10090 to "tv.cinema", 10091 to "tv.search"),
            allowed = setOf("tv.cinema", "tv.search"),
            leave = setOf("tv.cinema"),
        )

        assertTrue(fed.pumpsTo(cinema, owned = null))
        // Given a feed only as Lumbina's recognizer: recording by itself, it is asked about first.
        assertFalse(fed.pumpsTo(search, owned = null))
        // Recording for a session of Lumbina's own, which sends the sound itself.
        assertFalse(fed.pumpsTo(cinema, owned = 10090))
        assertTrue(fed.pumpsTo(cinema, owned = 10091))
        // A recording that stopped is always the pump's to hear of.
        assertTrue(fed.pumpsTo(HelperLine.Recording(10091, on = false), owned = 10091))
    }

    @Test
    fun `anything else is ignored, not fatal`() {
        assertNull(HelperLine.of("rec x 1"))
        assertNull(HelperLine.of("rec 10089"))
        assertNull(HelperLine.of("rec 10089 yes"))
        assertNull(HelperLine.of(""))
        assertNull(HelperLine.of("go"))
        assertNull(HelperLine.of("heard"))
        assertNull(HelperLine.of("heard x"))
        assertNull(HelperLine.of("heard 10090 1"))
    }

    @Test
    fun `an app heard recording is asked about, unless the user answered already`() {
        assertEquals(HeardApp.Ask, HeardApp.of("tv.cinema", 10090, allowed = emptySet(), uidNow = 10090))
        assertEquals(HeardApp.Ask, HeardApp.of("tv.cinema", 10090, allowed = setOf("tv.radio"), uidNow = 10090))
    }

    @Test
    fun `an allowed app that came after the helper started gets a new helper, once`() {
        val allowed = setOf("tv.cinema")
        assertEquals(HeardApp.Restart, HeardApp.of("tv.cinema", 10090, allowed, uidNow = 10090))
        // Not the uid a new helper would be given: starting it again would only loop.
        assertEquals(HeardApp.Ignore, HeardApp.of("tv.cinema", 1010090, allowed, uidNow = 10090))
        assertEquals(HeardApp.Ignore, HeardApp.of("tv.cinema", 10090, allowed, uidNow = null))
    }
}
