package dev.lumbina.tv.core.data.home

import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.RecommendationsView
import dev.lumbina.tv.core.domain.home.RowLook
import dev.lumbina.tv.core.domain.home.TileNames
import dev.lumbina.tv.core.domain.home.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAppearanceStoreTest {

    @Test
    fun `the look reads back as it was written`() {
        val look = HomeAppearance(
            names = TileNames.Focused,
            namesPlace = NamesPlace.Under,
            rowLooks = mapOf(
                "favorites" to RowLook(plate = false, names = TileNames.All, namesPlace = NamesPlace.OnTile),
                "recent" to RowLook(tileSize = TileSize.Large, plate = true),
            ),
            recommendationsView = RecommendationsView.PerChannel,
            channelOrder = listOf("kino/new", "tube/top"),
        )
        assertEquals(look, look.toStored().toDomain())
    }

    @Test
    fun `names untouched before stay off`() {
        assertEquals(TileNames.None, StoredHomeAppearance().toDomain().names)
    }

    @Test
    fun `names on every tile set for all rows before stay under them`() {
        val look = StoredHomeAppearance(showLabels = true).toDomain()
        assertEquals(
            TileNames.All to NamesPlace.Under,
            look.namesIn("category:films") to look.namesPlaceIn("category:films"),
        )
    }
}
