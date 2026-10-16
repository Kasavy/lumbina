package dev.lumbina.tv.feature.home

import androidx.compose.ui.unit.Density
import dev.lumbina.tv.core.domain.home.TileSize
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvenCellsTest {

    private val width = 1768

    /** The widths of the tiles of one line, each spanning [span] columns. */
    private fun tiles(span: Int): List<Int> {
        val columns = with(EvenCells(GRID_UNITS)) { Density(1f).calculateCrossAxisCellSizes(width, 0) }
        return columns.chunked(span).map { it.sum() }
    }

    @Test
    fun `tiles of every size fill the line exactly`() {
        TileSize.entries.forEach { size ->
            val line = tiles(size.gridSpan)
            assertEquals(size.name, size.perRow, line.size)
            assertEquals(size.name, width, line.sum())
        }
    }

    @Test
    fun `tiles of one size are the same width to the pixel`() {
        TileSize.entries.forEach { size ->
            val exact = width.toFloat() / size.perRow
            assertTrue(size.name, tiles(size.gridSpan).all { abs(it - exact) <= 1f })
        }
    }
}
