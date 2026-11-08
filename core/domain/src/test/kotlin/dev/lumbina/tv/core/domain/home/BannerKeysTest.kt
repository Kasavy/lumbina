package dev.lumbina.tv.core.domain.home

import dev.lumbina.tv.core.domain.app.AppId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerKeysTest {

    @Test
    fun `"All apps" and inputs are Lumbina's own tiles, an app is not`() {
        assertTrue(isOwnTileKey(HomeItem.AllApps.key))
        assertTrue(isOwnTileKey(HomeItem.Input("hdmi/HW5").key))
        assertTrue(isInputKey(HomeItem.Input("hdmi/HW5").key))
        assertFalse(isInputKey(HomeItem.AllApps.key))
        assertFalse(isOwnTileKey(AppId("tv.cinema", "tv.cinema.Main").flatten()))
    }
}
