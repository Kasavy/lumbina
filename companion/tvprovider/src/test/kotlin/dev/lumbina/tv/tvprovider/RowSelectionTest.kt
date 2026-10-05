package dev.lumbina.tv.tvprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowSelectionTest {

    private val rows = listOf(
        mapOf("_id" to 1L, "internal_provider_id" to "a", "type" to 0, "weight" to 5.0),
        mapOf("_id" to 2L, "internal_provider_id" to "b", "type" to 1, "weight" to null),
        mapOf("_id" to 3L, "internal_provider_id" to "a", "type" to 1, "weight" to 9.0),
    )

    private fun select(selection: String?, vararg args: String) =
        RowSelection.select(rows, selection, arrayOf(*args)) { row, column -> row[column] }?.map { it["_id"] }

    private fun sort(order: String?) = RowSelection.sort(rows, order) { row, column -> row[column] }.map { it["_id"] }

    @Test
    fun `no selection matches everything`() {
        assertEquals(listOf(1L, 2L, 3L), select(null))
        assertEquals(listOf(1L, 2L, 3L), select("  "))
    }

    @Test
    fun `a placeholder matches only its rows`() {
        assertEquals(listOf(1L, 3L), select("internal_provider_id=?", "a"))
        assertEquals(listOf(2L), select("internal_provider_id = ?", "b"))
    }

    @Test
    fun `conditions join with AND and numbers compare as text`() {
        assertEquals(listOf(3L), select("internal_provider_id = ? and type = ?", "a", "1"))
        assertEquals(listOf(3L), select("_id = ?", "3"))
        assertEquals(listOf(1L), select("weight = ?", "5"))
    }

    @Test
    fun `literals and IS NULL`() {
        assertEquals(listOf(1L, 3L), select("internal_provider_id = 'a'"))
        assertEquals(listOf(2L, 3L), select("type=1"))
        assertEquals(listOf(2L), select("weight IS NULL"))
        assertEquals(emptyList<Long>(), select("internal_provider_id = 'it''s'"))
    }

    @Test
    fun `what it can't read matches nothing rather than everything`() {
        assertNull(select("internal_provider_id LIKE ?", "a"))
        assertNull(select("type = ? OR type = ?", "0", "1"))
        assertNull(select("type = ?"))
        assertNull(select("type = ?", "0", "1"))
        assertNull(select(null, "a"))
    }

    @Test
    fun `sorts by columns, nulls first, descending when asked`() {
        assertEquals(listOf(2L, 1L, 3L), sort("weight"))
        assertEquals(listOf(3L, 1L, 2L), sort("weight DESC"))
        assertEquals(listOf(1L, 3L, 2L), sort("internal_provider_id ASC, _id"))
        assertEquals(listOf(3L, 1L, 2L), sort("internal_provider_id, _id desc"))
    }

    @Test
    fun `an unreadable order keeps the stored order`() {
        assertEquals(listOf(1L, 2L, 3L), sort(null))
        assertEquals(listOf(1L, 2L, 3L), sort("weight * 2"))
    }
}
