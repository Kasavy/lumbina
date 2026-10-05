package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.lumbina.tv.core.domain.home.TileSize

/*
 * Outlines of what is still loading, so a cold start (slow devices, a busy boot) shows the
 * home screen's shape at once and rows don't push each other down as they arrive.
 */

/** Favorites' title and a row of empty tiles, while apps and the layout load. */
internal fun LazyGridScope.homeLoading(title: String, size: TileSize) {
    fullWidth("loading:title") { SectionTitle(title) }
    items((0 until size.columns).toList(), key = { "loading:$it" }) {
        Placeholder(Modifier.aspectRatio(BANNER_RATIO))
    }
}

/** A program row's title and empty cards as tall as the row will be, until the TV Provider answers. */
internal fun LazyGridScope.programRowLoading(key: String, title: String, size: TileSize) {
    fullWidth("title:$key") { SectionTitle(title) }
    fullWidth("row:$key") {
        Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
            repeat(LOADING_CARDS) {
                Placeholder(Modifier.width(size.rowTileWidth).height(programRowHeight(emptyList(), size)))
            }
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            MaterialTheme.colorScheme.onSurface.copy(alpha = PLACEHOLDER_ALPHA),
            RoundedCornerShape(12.dp),
        ),
    )
}

private const val BANNER_RATIO = 16f / 9f
private const val PLACEHOLDER_ALPHA = 0.08f
private const val LOADING_CARDS = 4
