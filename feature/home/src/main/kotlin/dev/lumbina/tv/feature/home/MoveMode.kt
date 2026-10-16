package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.ui.DIMMED_ALPHA
import dev.lumbina.tv.core.ui.PillShape

/** The tile being moved and the tile whose menu is open, both marked with the accent. */
internal data class Marks(val moving: Moving?, val menuTile: Moving?)

/** The name of the tile being moved, as on screen ([allApps] for the shortcut); null when none is. */
internal fun HomeUiState.nameOf(tile: Moving?, allApps: String): String? {
    val moved = tile ?: return null
    val found = content.sections.firstOrNull { it.id == moved.sectionId }?.tiles?.firstOrNull { it.key == moved.key }
    return when (found) {
        is TileContent.AppTile -> found.app.label
        is TileContent.FolderTile -> found.name
        TileContent.AllAppsTile -> allApps
        null -> null
    }
}

/**
 * What the bottom of home says: while a tile is being moved, its name and what the keys do; else
 * what just [failed] to open; else the "hold OK" hint when it is due.
 */
@Composable
internal fun HomeHints(holdOk: Boolean, moving: String?, modifier: Modifier = Modifier, failed: Failed? = null) {
    if (moving == null && failed == null) {
        HoldOkHint(visible = holdOk, modifier = modifier)
        return
    }
    Text(
        text = if (moving != null) stringResource(R.string.move_hint, moving) else failed?.message().orEmpty(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier
            .padding(bottom = BOTTOM_MESSAGE_GAP)
            .bottomMessage()
            .padding(horizontal = 26.dp, vertical = 14.dp),
    )
}

/** A message at the bottom of home (a move, a failure, "Undo"): a pill in the inverse of the theme. */
@Composable
internal fun Modifier.bottomMessage(): Modifier = background(MaterialTheme.colorScheme.inverseSurface, PillShape)

/** Between a message at the bottom and the screen's edge. */
internal val BOTTOM_MESSAGE_GAP = 28.dp

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

/**
 * The keys of a tile that may be [moving]: all of them are the move's while it is. OK puts the
 * tile down as it goes down, so its release comes when the tile is a plain tile again, and would
 * open the app: that release is swallowed.
 */
@Composable
internal fun Modifier.moveKeys(moving: Boolean, actions: HomeActions): Modifier {
    val putDown = remember { mutableStateOf(false) }
    return onPreviewKeyEvent { event ->
        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
        when {
            // The release may come while the tile still counts as moving (a quick tap): then it is done with here.
            moving -> moveKey(event.key, event.type, actions).also {
                if (ok) putDown.value = event.type == KeyEventType.KeyDown
            }

            ok && putDown.value -> true.also { if (event.type == KeyEventType.KeyUp) putDown.value = false }

            else -> false
        }
    }
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
private const val HINT_SURFACE_ALPHA = 0.92f

/** Half out of the tile: the whole arrow still fits in a backing's inset, which clips what goes past it. */
internal val ARROW_OUTSIDE = 14.dp
