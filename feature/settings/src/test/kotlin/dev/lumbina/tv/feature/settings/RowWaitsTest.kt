package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.home.HomeRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowWaitsTest {

    @Test
    fun `a shown row that can't fill waits, its look hidden`() {
        assertTrue(rowWaits(HomeRow.Frequent, visible = true, usageGranted = false, channelsMissing = false))
        assertTrue(rowWaits(HomeRow.WatchNext, visible = true, usageGranted = true, channelsMissing = true))
        assertTrue(rowWaits(HomeRow.Recommendations, visible = true, usageGranted = true, channelsMissing = true))
    }

    @Test
    fun `a row switched off, or one that has what it needs, shows its look`() {
        assertFalse(rowWaits(HomeRow.Frequent, visible = false, usageGranted = false, channelsMissing = true))
        assertFalse(rowWaits(HomeRow.Frequent, visible = true, usageGranted = true, channelsMissing = true))
        assertFalse(rowWaits(HomeRow.WatchNext, visible = true, usageGranted = false, channelsMissing = false))
        assertFalse(rowWaits(HomeRow.Favorites, visible = true, usageGranted = false, channelsMissing = true))
    }
}
