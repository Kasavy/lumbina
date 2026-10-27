package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.channels.ChannelSettings
import dev.lumbina.tv.core.domain.channels.ProgramCard
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchNextChoicesTest {

    private fun card(id: Long, app: String) = ProgramCard(id, app, "t$id", null, null, null, null)

    @Test
    fun `apps by name with their count, a hidden one kept to put back, one not installed left out`() {
        val choices = watchNextChoices(
            watchNext = listOf(card(1, "tube"), card(2, "kino"), card(3, "tube"), card(4, "gone")),
            listed = setOf("kino", "old"),
            settings = ChannelSettings(hiddenWatchNext = setOf("kino", "old")),
            labels = mapOf("tube" to "Tube", "kino" to "Kino", "old" to "Archive"),
        )
        assertEquals(
            listOf(
                WatchNextChoice("old", "Archive", 0, shown = false),
                WatchNextChoice("kino", "Kino", 1, shown = false),
                WatchNextChoice("tube", "Tube", 2, shown = true),
            ),
            choices,
        )
    }

    @Test
    fun `names sort as people read them, Yo among Ye`() {
        val choices = watchNextChoices(
            watchNext = listOf(card(1, "ya"), card(2, "yo"), card(3, "zh")),
            listed = emptySet(),
            settings = ChannelSettings(),
            labels = mapOf("ya" to "Я", "yo" to "Ёлка", "zh" to "Жара"),
            locale = Locale.forLanguageTag("ru"),
        )
        assertEquals(listOf("Ёлка", "Жара", "Я"), choices.map { it.app })
    }
}
