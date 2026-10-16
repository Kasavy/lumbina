package dev.lumbina.tv.core.domain.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRowsTest {

    private fun keys(entries: List<HomeRowEntry>) = entries.map { it.row.key }

    @Test
    fun `default order starts with favorites and puts categories between the built-in rows`() {
        assertEquals(
            listOf(
                "favorites",
                "watch_next",
                "opened",
                "frequent",
                "recent",
                "category:kids",
                "recommendations",
                "all_apps",
            ),
            keys(arrangeRows(emptyList(), emptySet(), listOf("kids"))),
        )
    }

    @Test
    fun `the user's order wins, a row new to it goes after the one it follows by default, gone ones are dropped`() {
        val rows = arrangeRows(
            order = listOf("recommendations", "category:gone", "watch_next"),
            hidden = setOf("recent"),
            categoryIds = listOf("kids"),
        )
        assertEquals(
            listOf(
                "favorites",
                "recommendations",
                "all_apps",
                "watch_next",
                "opened",
                "frequent",
                "recent",
                "category:kids",
            ),
            keys(rows),
        )
        assertEquals(listOf(true, true, true, true, false, false, false, true), rows.map { it.visible })
    }

    @Test
    fun `an order saved before favorites was a row gets favorites first`() {
        val saved = listOf("watch_next", "opened", "frequent", "recent", "recommendations", "all_apps")
        assertEquals("favorites", keys(arrangeRows(saved, emptySet(), emptyList())).first())
    }

    @Test
    fun `a new category goes after the last one, above the recommendations, wherever apps stand`() {
        val last =
            keys(arrangeRows(listOf("recent", "category:films", "all_apps"), emptySet(), listOf("films", "kids")))
        assertEquals(listOf("category:films", "category:kids"), last.filter { it.startsWith("category:") })
        assertEquals(last.indexOf("category:films") + 1, last.indexOf("category:kids"))
        val moved = keys(arrangeRows(listOf("all_apps", "recent"), emptySet(), listOf("kids")))
        assertEquals(moved.indexOf("recent") + 1, moved.indexOf("category:kids"))
        assertTrue(moved.indexOf("all_apps") < moved.indexOf("recent"))
        assertTrue(moved.indexOf("category:kids") < moved.indexOf("recommendations"))
    }

    @Test
    fun `frequently used starts off and shows once switched on`() {
        val look = HomeAppearance()
        assertFalse(look.rows(emptyList()).first { it.row == HomeRow.Frequent }.visible)
        val on = look.withRowToggled(HomeRow.Frequent)
        assertTrue(on.rows(emptyList()).first { it.row == HomeRow.Frequent }.visible)
        assertEquals(look, on.withRowToggled(HomeRow.Frequent))
    }

    @Test
    fun `favorites and apps move and switch off like any row`() {
        val look = HomeAppearance(rowOrder = listOf("all_apps", "favorites"))
        assertEquals(listOf(HomeRow.AllApps, HomeRow.Favorites), look.rows(emptyList()).take(2).map { it.row })
        val off = look.withRowToggled(HomeRow.AllApps).withRowToggled(HomeRow.Favorites)
        assertFalse(off.rows(emptyList()).first { it.row == HomeRow.AllApps }.visible)
        assertFalse(off.rows(emptyList()).first { it.row == HomeRow.Favorites }.visible)
    }

    @Test
    fun `moving stays within the list`() {
        val rows = arrangeRows(emptyList(), emptySet(), listOf("kids"))
        assertEquals(
            listOf(
                "favorites",
                "watch_next",
                "frequent",
                "opened",
                "recent",
                "category:kids",
                "recommendations",
                "all_apps",
            ),
            rows.movedRow("frequent", -1),
        )
        assertEquals(
            listOf(
                "watch_next",
                "favorites",
                "opened",
                "frequent",
                "recent",
                "category:kids",
                "recommendations",
                "all_apps",
            ),
            rows.movedRow("watch_next", -5),
        )
    }

    @Test
    fun `a section's row is favorites or its category`() {
        assertEquals(HomeRow.Favorites, HomeRow.ofSection(HomeSection.FAVORITES))
        assertEquals(HomeRow.Category("kids"), HomeRow.ofSection("kids"))
    }
}
