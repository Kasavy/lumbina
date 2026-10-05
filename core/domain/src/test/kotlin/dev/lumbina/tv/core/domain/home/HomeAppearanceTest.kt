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
}
