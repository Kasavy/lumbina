package dev.lumbina.tv.feature.home

import dev.lumbina.tv.core.domain.home.TileSize
import org.junit.Assert.assertTrue
import org.junit.Test

class TileWidthTest {

    @Test
    fun `a line of tiles on a backing fits it, and wastes less than a pixel a tile`() {
        for (width in 600..4000) {
            for (gap in listOf(13, 20, 27, 40, 60, 80)) {
                TileSize.entries.forEach { size ->
                    val columns = size.perRow
                    val line = tileWidthPx(width, gap, columns) * columns + gap * (columns - 1)
                    assertTrue("$width/$gap/$columns", line <= width && width - line < columns)
                }
            }
        }
    }
}
