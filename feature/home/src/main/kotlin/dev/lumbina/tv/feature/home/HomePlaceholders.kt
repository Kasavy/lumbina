package dev.lumbina.tv.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.BANNER_ASPECT

/*
 * Outlines of what is still loading, so a cold start (slow devices, a busy boot) shows the
 * home screen's shape at once and rows don't push each other down as they arrive.
 */

/** Favorites' title and a row of empty tiles, while apps and the layout load. */
internal fun LazyGridScope.homeLoading(title: String, size: TileSize) {
    fullWidth("loading:title") { SectionTitle(title) }
    items((0 until size.columns).toList(), key = { "loading:$it" }, span = { GridItemSpan(size.gridSpan) }) {
        Placeholder(Modifier.gridCell().aspectRatio(BANNER_ASPECT))
    }
}

/** A program row's title and empty cards as tall as the row will be, until the TV Provider answers. */
internal fun LazyGridScope.programRowLoading(key: String, title: String?, size: TileSize) {
    if (title != null) fullWidth("title:$key") { SectionTitle(title) }
    fullWidth("row:$key") {
        Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
            repeat(LOADING_CARDS) {
                Placeholder(Modifier.width(size.rowTileWidth).height(programRowHeight(emptyList(), size)))
            }
        }
    }
}

/**
 * A row of programs that can't be read yet: the TV Provider wants a [permission] first. Its title,
 * why it is empty, and two buttons: let Lumbina read the channels, or switch the row off.
 */
internal fun LazyGridScope.needsAccess(row: HomeRow, title: String, permission: String, actions: HomeActions) {
    fullWidth("title:${row.key}") { SectionTitle(title) }
    fullWidth("row:${row.key}") {
        val focus = LocalHomeFocus.current
        val focusManager = LocalFocusManager.current
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            actions.channelsAccess(it)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = PLACEHOLDER_ALPHA),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.channels_need_access),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { ask.launch(permission) },
                modifier = Modifier.homeFocus(focus, FocusKey(row.key, "allow")),
            ) { Text(stringResource(R.string.channels_allow)) }
            OutlinedButton(
                onClick = {
                    // The button is about to go: the focus goes up to the row above first.
                    focusManager.moveFocus(FocusDirection.Up)
                    actions.hideRow(row, title)
                },
                modifier = Modifier.homeFocus(focus, FocusKey(row.key, "hide")),
            ) { Text(stringResource(R.string.channels_hide_row)) }
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

private const val PLACEHOLDER_ALPHA = 0.08f
private const val LOADING_CARDS = 4
