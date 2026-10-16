package dev.lumbina.tv.core.domain.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeAppearanceTest {

    @Test
    fun `a banner is set, replaced and taken back`() {
        val set = HomeAppearance().withBanner("app", "content://a").withBanner("other", "content://o")
        assertEquals("content://b", set.withBanner("app", "content://b").customBanners["app"])
        assertEquals(mapOf("other" to "content://o"), set.withBanner("app", null).customBanners)
    }

    @Test
    fun `a row follows the general tile size and names until it gets its own`() {
        val general = HomeAppearance(tileSize = TileSize.Small, names = TileNames.All, namesPlace = NamesPlace.Under)
        assertEquals(TileSize.Small, general.tileSizeOf("recent"))
        // Favorites, the row of the apps used most, starts a size bigger: five in a row.
        assertEquals(TileSize.Medium, general.tileSizeOf("favorites"))
        assertEquals(TileNames.All, general.namesIn("favorites"))
        assertEquals(NamesPlace.Under, general.namesPlaceIn("favorites"))

        val own = general.withRowLook("favorites") {
            it.copy(tileSize = TileSize.Large, names = TileNames.Focused, namesPlace = NamesPlace.OnTile)
        }
        assertEquals(TileSize.Large, own.tileSizeOf("favorites"))
        assertEquals(TileNames.Focused, own.namesIn("favorites"))
        assertEquals(NamesPlace.OnTile, own.namesPlaceIn("favorites"))
        assertEquals(TileSize.Small, own.tileSizeOf("recent"))
    }

    @Test
    fun `apps start without names, programs with their titles under them`() {
        val look = HomeAppearance()
        assertEquals(TileNames.None, look.namesIn("favorites"))
        assertEquals(NamesPlace.OnTile, look.namesPlaceIn("category:films"))
        assertEquals(TileNames.All, look.namesIn("watch_next"))
        assertEquals(NamesPlace.Under, look.namesPlaceIn("recommendations"))
        assertEquals(TileNames.All, look.namesIn(channelRowKey("pkg/news")))
    }

    @Test
    fun `only favorites stands on its backing until a row says otherwise`() {
        val look = HomeAppearance()
        assertEquals(true, look.plateIn("favorites"))
        assertEquals(false, look.plateIn("category:films"))
        val switched = look.withRowLook("favorites") { it.copy(plate = false) }
            .withRowLook("recent") { it.copy(plate = true) }
        assertEquals(false, switched.plateIn("favorites"))
        assertEquals(true, switched.plateIn("recent"))
    }

    @Test
    fun `a row set back to the general look is not kept`() {
        val own = HomeAppearance().withRowLook("recent") { it.copy(tileSize = TileSize.Large) }
        assertEquals(setOf("recent"), own.rowLooks.keys)
        assertEquals(emptyMap<String, RowLook>(), own.withRowLook("recent") { it.copy(tileSize = null) }.rowLooks)
    }
}
