package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.TileContent

/** The tile being moved and the tile whose menu is open, both marked with the accent. */
internal data class Marks(val moving: Moving?, val menuTile: Moving?)

@Composable
internal fun TileContent.name(): String = when (this) {
    is TileContent.AppTile -> app.label
    is TileContent.FolderTile -> name
}

/** The row's title with what the keys do while one of its tiles is being moved. */
@Composable
internal fun MovingTitle(title: String, tile: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.move_hint, tile),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = HINT_SURFACE_ALPHA),
                    RoundedCornerShape(percent = PILL),
                )
                .padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

/** A tile being moved: a little bigger and raised. */
internal fun Modifier.lifted(): Modifier = zIndex(1f).graphicsLayer {
    scaleX = LIFT_SCALE
    scaleY = LIFT_SCALE
    translationY = -LIFT_UP.toPx()
}

internal fun Modifier.dimmedIf(dimmed: Boolean): Modifier = if (dimmed) alpha(DIMMED_ALPHA) else this

@Composable
internal fun MoveArrow(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .zIndex(2f)
            .size(28.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = HINT_SURFACE_ALPHA), CircleShape),
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

/** Handles a key while a tile is being moved; returns true when the key is consumed. */
internal fun moveKey(key: Key, type: KeyEventType, actions: HomeActions): Boolean {
    if (type != KeyEventType.KeyDown) return true
    when (key) {
        Key.DirectionLeft -> actions.move(-1)
        Key.DirectionRight -> actions.move(1)
        Key.DirectionCenter, Key.Enter -> actions.stopMoving()
        Key.Back, Key.Escape -> actions.cancelMoving()
        else -> Unit
    }
    return true
}

private const val LIFT_SCALE = 1.08f
private val LIFT_UP = 7.dp
private const val DIMMED_ALPHA = 0.4f
private const val HINT_SURFACE_ALPHA = 0.92f
internal val ARROW_OUTSIDE = 22.dp
