package dev.lumbina.tv.core.domain.channels

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecommendationsTest {

    private fun program(app: String, id: Long) = ProgramCard(id, app, "$app $id", null, null, ArtShape.Wide, null)

    private fun feed(app: String, name: String, vararg ids: Long) =
        ChannelFeed(TvChannel(1, app, name, null, null), ids.map { program(app, it) })

    @Test
    fun `the most used app's channels come first, then apps being watched`() {
        val feeds = listOf(feed("vk", "main"), feed("kino", "new"), feed("tube", "top"))
        val ordered = feeds.byAppUse(usage = mapOf("tube" to 5_000L), watching = setOf("kino"))
        assertEquals(listOf("tube", "kino", "vk"), ordered.map { it.channel.packageName })
    }

    @Test
    fun `the mix takes one program from each app in turn`() {
        val feeds = listOf(feed("tube", "a", 1, 2), feed("tube", "b", 3), feed("kino", "c", 10, 11, 12))
        val mixed = mixedPrograms(feeds, limit = 5)
        assertEquals(listOf(1L, 10L, 2L, 11L, 3L), mixed.map { it.id })
    }
}
