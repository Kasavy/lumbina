package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.LINE_SEPARATOR
import dev.lumbina.tv.core.ui.line

// Rows that can be picked up and moved with ▲ ▼: the rows of home, and the channels' own rows
// under the recommendations.

/** The channels' own rows, each under the recommendations: OK shows or hides one, holding OK moves it among them. */
internal fun LazyListScope.channelItems(
    state: HomeSettingsState,
    moving: MovingRow?,
    onMoving: (MovingRow?) -> Unit,
    commands: HomeSettingsCommands,
) {
    state.channels.forEachIndexed { position, channel ->
        item(key = "channel:${channel.key}") {
            val isMoving = moving?.channel == true && moving.key == channel.key
            MovableRow(
                title = line(channel.app, channel.name),
                hint = listOf(
                    stringResource(if (channel.shown) R.string.home_category_shown else R.string.home_category_hidden),
                    stringResource(R.string.home_row_channel),
                ).joinToString(LINE_SEPARATOR),
                isMoving = isMoving,
                position = position,
                dimmed = moving != null && !isMoving,
                modifier = Modifier.padding(start = CHANNEL_INDENT),
                // Its switch takes the room the hint has on other rows; its own line says what the switch does.
                holdHintBelow = true,
                onClick = {
                    if (isMoving) {
                        onMoving(null)
                    } else if (moving == null) {
                        commands.toggleChannel(channel.key)
                    }
                },
                onLongClick = {
                    if (moving == null) onMoving(MovingRow(channel.key, state.channels.map { it.key }, channel = true))
                },
                onMoveKey = { pressed -> moving?.let { moveKey(pressed, it, commands, onMoving) } },
            ) {
                Switch(checked = channel.shown, onCheckedChange = null)
            }
        }
    }
}

/** ▲ ▼ move the row, OK puts it down, Back puts the rows back as they were. */
internal fun moveKey(key: Key, moving: MovingRow, commands: HomeSettingsCommands, onMoving: (MovingRow?) -> Unit) {
    val move = { by: Int ->
        if (moving.channel) commands.moveChannel(moving.key, by) else commands.moveRow(moving.key, by)
    }
    when (key) {
        Key.DirectionUp -> move(-1)

        Key.DirectionDown -> move(1)

        Key.DirectionCenter, Key.Enter -> onMoving(null)

        Key.Back, Key.Escape -> {
            if (moving.channel) commands.setChannelOrder(moving.before) else commands.setRowOrder(moving.before)
            onMoving(null)
        }

        else -> Unit
    }
}

/**
 * A row that can be picked up: a handle on the left, and while focused a reminder that holding
 * OK moves it, at the right or, with [holdHintBelow], in place of the [hint]. While [isMoving] it
 * is outlined, and [onMoveKey] gets every key press.
 */
@Suppress("LongParameterList") // one row's look and what it does
@Composable
internal fun MovableRow(
    title: String,
    hint: String,
    isMoving: Boolean,
    position: Int,
    dimmed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveKey: (Key) -> Unit,
    modifier: Modifier = Modifier,
    holdHintBelow: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val keys = remember(isMoving) { MoveKeys() }
    // A moving row keeps itself on screen as it goes past the list's edge.
    val view = remember { BringIntoViewRequester() }
    LaunchedEffect(isMoving, position) { if (isMoving) view.bringIntoView() }
    val accent = MaterialTheme.colorScheme.primary
    SettingRow(
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (isMoving) {
                Icon(painterResource(R.drawable.ic_unfold), contentDescription = null, modifier = Modifier.size(28.dp))
            } else {
                RowIcon(R.drawable.ic_drag_indicator, contentDescription = null)
            }
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // While moving, the keys replace the description, right where the eye is.
        supportingContent = if (isMoving) {
            { Text(stringResource(R.string.move_row_hint)) }
        } else if (focused && holdHintBelow) {
            { HoldOkHint(R.drawable.ic_unfold) }
        } else {
            // One line: the hold hint takes room on the focused row, which must not grow.
            hint.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (focused && !isMoving && !holdHintBelow) HoldOkHint(R.drawable.ic_unfold)
                trailing()
            }
        },
        outline = accent.takeIf { isMoving },
        dimmed = dimmed,
        modifier = modifier
            .bringIntoViewRequester(view)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                isMoving && keys.handle(event.type, event.key, event.nativeKeyEvent.repeatCount, onMoveKey)
            },
    )
}

/** The channels' rows stand under the recommendations, set in a little: they are its parts. */
private val CHANNEL_INDENT = 24.dp
