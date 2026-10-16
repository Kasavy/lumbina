package dev.lumbina.tv.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowKeysTest {

    @Test
    fun `a row owns its heading, its tiles, its empty line and its own block`() {
        listOf("title:recent", "row:recent", "empty:recent", "recent", "recent:com.app/.Main").forEach {
            assertTrue(it, isOfRow(it, "recent"))
        }
    }

    @Test
    fun `a row whose name begins like another's is not taken for it`() {
        assertFalse(isOfRow("recommendations", "recent"))
        assertFalse(isOfRow("title:recommendations", "recent"))
        assertFalse(isOfRow("recent", "recommendations"))
        assertFalse(isOfRow("all_apps:com.app/.Main", "all"))
    }

    @Test
    fun `what is not a text key belongs to no row`() {
        assertFalse(isOfRow(42, "recent"))
    }
}
