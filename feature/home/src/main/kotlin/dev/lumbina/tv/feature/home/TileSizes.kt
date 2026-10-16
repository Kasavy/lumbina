package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.lumbina.tv.core.domain.home.TileSize
import kotlin.math.roundToInt

/** Padding and spacing of the home screen's grid. */
internal val GRID_SIDE = 48.dp
internal val GRID_SPACING = 20.dp

/**
 * The grid is laid out with no spacing between its columns, and every item keeps half the spacing
 * at either side ([gridCell]): with spacing between columns a tile's width would be several
 * columns narrower than the spacing itself, which the grid's arrangement does not lay out right.
 */
internal val GRID_PADDING = PaddingValues(horizontal = GRID_SIDE - GRID_SPACING / 2, vertical = 32.dp)

/** For every item of the home grid: its half of the spacing to its neighbours. */
internal fun Modifier.gridCell(): Modifier = padding(horizontal = GRID_SPACING / 2)

/**
 * The home grid's columns: a number every count of tiles per row (4 to 8) divides, so that
 * sections of different sizes share one grid, each tile spanning [gridSpan] of them.
 */
internal const val GRID_UNITS = 840

/** How many of the home grid's [GRID_UNITS] one tile takes. */
internal val TileSize.gridSpan: Int get() = GRID_UNITS / columns

/** Tiles per row of a grid. */
internal val TileSize.columns: Int get() = perRow

/**
 * [count] columns of the same width to the pixel. [GridCells.Fixed] rounds each column down and
 * gives the pixels left over to the first ones: with as many columns as home has, the first tile
 * of a line would be dozens of pixels wider than the last. Here each column ends where it should,
 * so no tile is more than a pixel off.
 */
internal class EvenCells(private val count: Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val column = (availableSize - spacing * (count - 1)).toFloat() / count
        var edge = 0
        return List(count) { index ->
            val next = (column * (index + 1)).roundToInt()
            (next - edge).also { edge = next }
        }
    }

    override fun equals(other: Any?): Boolean = other is EvenCells && other.count == count

    override fun hashCode(): Int = count
}

/**
 * How wide an app tile is in a row: as wide as a cell of the grid, so that a row shows as many
 * tiles as the size says ("6 in a row") and a row and a grid of one size line up. On a backing the
 * tiles are narrower by its [inset] at either side, and as many fit.
 */
@Composable
internal fun TileSize.appTileWidth(inset: Dp = 0.dp): Dp {
    val screen = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return (screen - GRID_SIDE * 2 - inset * 2 - GRID_SPACING * (columns - 1)) / columns
}

/** How wide a program card is in "Continue watching" and the channels' rows. */
internal val TileSize.rowTileWidth: Dp
    get() = when (this) {
        TileSize.Smallest -> 136.dp
        TileSize.Smaller -> 152.dp
        TileSize.Small -> 176.dp
        TileSize.Medium -> 208.dp
        TileSize.Large -> 256.dp
    }
