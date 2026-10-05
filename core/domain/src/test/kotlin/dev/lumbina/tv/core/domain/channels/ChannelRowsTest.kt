package dev.lumbina.tv.core.domain.channels

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChannelRowsTest {

    private fun feed(app: String, name: String) =
        ChannelFeed(TvChannel(1, app, name, null, null), listOf(ProgramCard(1, app, name, null, null, null, null)))

    private val labels = mapOf("tube" to "Tube", "kino" to "Kino")

    @Test
    fun `a mix comes first when the channels are from more than one app`() {
        val rows = listOf(feed("tube", "top"), feed("kino", "new")).visibleRows(ChannelSettings(), labels)
        assertTrue(rows.first().isMix)
        assertEquals(listOf("Tube", "Kino"), rows.drop(1).map { it.app })
    }

    @Test
    fun `hidden channels and apps no longer installed are left out, and one app needs no mix`() {
        val feeds = listOf(feed("tube", "top"), feed("tube", "news"), feed("gone", "x"), feed("kino", "new"))
        val rows = feeds.visibleRows(ChannelSettings(hiddenChannels = setOf("kino/new")), labels)
        assertEquals(listOf("top", "news"), rows.map { it.channel })
    }
}
