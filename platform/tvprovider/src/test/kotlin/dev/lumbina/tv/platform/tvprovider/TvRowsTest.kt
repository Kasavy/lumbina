package dev.lumbina.tv.platform.tvprovider

import dev.lumbina.tv.core.domain.channels.ArtShape
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.channels.TvChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvRowsTest {

    private fun row(vararg values: Pair<String, Any?>) = object : TvRow {
        private val map = values.toMap()

        override fun string(column: String): String? = map[column]?.toString()

        override fun long(column: String): Long? =
            (map[column] as? Number)?.toLong() ?: map[column]?.toString()?.toLongOrNull()
    }

    @Test
    fun `reads a channel`() {
        val channel = channelOf(
            row("_id" to 1, "package_name" to "com.vk.tv", "display_name" to "Recommended", "browsable" to 1),
        ) { "logo/$it" }
        assertEquals(TvChannel(1, "com.vk.tv", "Recommended", null, "logo/1"), channel)
        assertNull(channelOf(row("_id" to 2, "package_name" to "x", "display_name" to "Y", "browsable" to 0)) { "" })
    }

    @Test
    fun `reads a VK preview program`() {
        val card = programOf(
            row(
                "_id" to 2,
                "package_name" to "com.vk.tv",
                "channel_id" to 1,
                "title" to "Машина",
                "poster_art_uri" to "https://img/1.jpg",
                "poster_art_aspect_ratio" to 0,
                "intent_uri" to "intent://video/1#Intent;scheme=vkvideo;end",
                "short_description" to "",
            ),
        )
        assertEquals(
            ProgramCard(
                2,
                "com.vk.tv",
                "Машина",
                null,
                "https://img/1.jpg",
                ArtShape.Wide,
                "intent://video/1#Intent;scheme=vkvideo;end",
            ),
            card,
        )
    }

    @Test
    fun `reads a Kinopoisk watch-next program with progress and no shape of its own`() {
        val card = programOf(
            row(
                "_id" to 12, "package_name" to "ru.kinopoisk.tv", "title" to "Re:ZERO",
                "episode_title" to "Re:ZERO", "season_display_number" to "4", "episode_display_number" to "18",
                "poster_art_uri" to "https://img/460x690", "last_playback_position_millis" to 835_000,
                "duration_millis" to 1_424_000,
            ),
        )!!
        assertEquals("S4 · E18", card.subtitle)
        assertNull(card.shape)
        assertEquals(835f / 1424f, card.progress!!, 0.001f)
    }

    @Test
    fun `skips programs without a title`() {
        assertNull(programOf(row("_id" to 3, "package_name" to "x", "title" to " ")))
    }

    @Test
    fun `a program in another app's channel is left out of it`() {
        val kino = TvChannel(1, "kino", "New", null, null)
        fun card(id: Long, app: String) = ProgramCard(id, app, "t$id", null, null, null, null)
        val feeds = feedsOf(
            listOf(kino),
            listOf(ChannelProgram(1, card(1, "kino"), weight = 1), ChannelProgram(1, card(2, "spoof"), weight = 9)),
        )
        assertEquals(listOf(1L), feeds.single().programs.map { it.id })
        assertEquals(emptyList<Any>(), feedsOf(listOf(kino), listOf(ChannelProgram(1, card(3, "spoof"), 1))))
    }

    @Test
    fun `an app's channels of one name make one feed, its programs by weight across both`() {
        fun card(id: Long) = ProgramCard(id, "tube", "t$id", null, null, null, null)
        val feeds = feedsOf(
            listOf(TvChannel(1, "tube", "Top", null, null), TvChannel(2, "tube", "Top", null, null)),
            listOf(ChannelProgram(1, card(1), weight = 1), ChannelProgram(2, card(2), weight = 5)),
        )
        assertEquals(listOf(2L, 1L), feeds.single().programs.map { it.id })
    }

    @Test
    fun `the census counts each app's rows, and the channels the home screen leaves out`() {
        val census = ProviderCensus()
        census.channel(row("package_name" to "com.vk.tv", "display_name" to "Recommended", "browsable" to 1))
        census.channel(row("package_name" to "com.vk.tv", "display_name" to "New", "browsable" to 0))
        census.channel(row("package_name" to "com.vk.tv", "display_name" to " ", "browsable" to 0))
        census.channel(row("package_name" to "a.first", "display_name" to "One"))
        repeat(3) { census.program(row("package_name" to "com.vk.tv")) }
        census.program(row("title" to "of nobody"))

        assertEquals(
            listOf(
                "(no package): 0 channels (0 not browsable, 0 unnamed), 1 programs",
                "a.first: 1 channels (0 not browsable, 0 unnamed), 0 programs",
                "com.vk.tv: 3 channels (2 not browsable, 1 unnamed), 3 programs",
            ),
            census.lines(),
        )
    }
}
