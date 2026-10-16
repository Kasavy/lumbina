package dev.lumbina.tv.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.lumbina.tv.core.ui.SCRIM_ALPHA

/**
 * Over home while the settings work on the row [rowKey]: the dimming the settings' panel would
 * lay over all of home, here with a window across the row, its heading and tiles, so what is
 * changed shows at full brightness. A row that is not laid out (switched off, empty, scrolled
 * away) leaves home dimmed all over.
 */
@Composable
internal fun SpotlightShade(grid: LazyGridState, rowKey: String, modifier: Modifier = Modifier) {
    val id = rowId(rowKey)
    Canvas(modifier.fillMaxSize()) {
        // Read while drawing: the window follows the grid as it scrolls, without recomposing.
        val items = grid.layoutInfo.visibleItemsInfo.filter { isOfRow(it.key, id) }
        val dim = Color.Black.copy(alpha = SCRIM_ALPHA)
        if (items.isEmpty()) {
            drawRect(dim)
            return@Canvas
        }
        // Items are measured from where the content starts, inside the grid's top padding.
        val shift = -grid.layoutInfo.viewportStartOffset
        val margin = WINDOW_MARGIN.toPx()
        val top = (items.minOf { it.offset.y } + shift - margin).coerceIn(0f, size.height)
        val bottom = (items.maxOf { it.offset.y + it.size.height } + shift + margin).coerceIn(top, size.height)
        drawRect(dim, size = Size(size.width, top))
        drawRect(dim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
    }
}

/** Room around the row inside the window: the focused tile grows past the row's own bounds. */
private val WINDOW_MARGIN = 12.dp
