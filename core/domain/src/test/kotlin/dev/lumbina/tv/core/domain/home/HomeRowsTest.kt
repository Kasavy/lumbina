package dev.lumbina.tv.core.domain.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRowsTest {

    private fun keys(entries: List<HomeRowEntry>) = entries.map { it.row.key }

    @Test
    fun `default order puts categories between the built-in rows`() {
        assertEquals(
            listOf("watch_next", "opened", "frequent", "recent", "category:kids", "recommendations"),
            keys(arrangeRows(emptyList(), emptySet(), listOf("kids"))),
        )
    }

    @Test
    fun `the user's order wins, new rows follow, gone ones are dropped`() {
        val rows = arrangeRows(
            order = listOf("recommendations", "category:gone", "watch_next"),
            hidden = setOf("recent"),
            categoryIds = listOf("kids"),
        )
        assertEquals(
            listOf("recommendations", "watch_next", "opened", "frequent", "recent", "category:kids"),
            keys(rows),
        )
        assertEquals(listOf(true, true, false, false, false, true), rows.map { it.visible })
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
    fun `toggling a row that starts on hides it`() {
        val off = HomeAppearance().withRowToggled(HomeRow.Recent)
        assertFalse(off.rows(emptyList()).first { it.row == HomeRow.Recent }.visible)
    }

    @Test
    fun `moving stays within the list`() {
        val rows = arrangeRows(emptyList(), emptySet(), listOf("kids"))
        assertEquals(
            listOf("watch_next", "frequent", "opened", "recent", "category:kids", "recommendations"),
            rows.movedRow("frequent", -1),
        )
        assertEquals(
            listOf("watch_next", "opened", "frequent", "recent", "category:kids", "recommendations"),
            rows.movedRow("watch_next", -5),
        )
    }
}
