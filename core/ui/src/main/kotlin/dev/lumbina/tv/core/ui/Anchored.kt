package dev.lumbina.tv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Where the focused tile is on screen, so a menu or the keyboard can open right under it.
 * Tiles report themselves with [anchorWhenFocused]; plain state, read only when a panel opens.
 */
class TileAnchor {
    var bounds: Rect? = null
        internal set
}

/** Tells [anchor] where this element is while it (or something inside it) has focus. */
fun Modifier.anchorWhenFocused(anchor: TileAnchor): Modifier = this then AnchorElement(anchor)

private data class AnchorElement(val anchor: TileAnchor) : ModifierNodeElement<AnchorNode>() {
    override fun create() = AnchorNode(anchor)

    override fun update(node: AnchorNode) {
        node.anchor = anchor
    }
}

private class AnchorNode(var anchor: TileAnchor) :
    Modifier.Node(),
    FocusEventModifierNode,
    GlobalPositionAwareModifierNode {
    private var focused = false
    private var placed: LayoutCoordinates? = null

    override fun onFocusEvent(focusState: FocusState) {
        focused = focusState.hasFocus
        if (focused) report()
    }

    // Every tile hears of every scroll; only the focused one works its bounds out.
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        placed = coordinates
        if (focused) report()
    }

    private fun report() {
        anchor.bounds = placed?.takeIf { it.isAttached }?.boundsInWindow()
    }
}

/**
 * A panel on top of everything, under [anchor] and lined up with its left edge; above it when
 * there is no room below. Without an anchor it sits at the top in the middle. Back closes it.
 */
@Composable
internal fun AnchoredPanel(anchor: Rect?, width: Dp, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Layout(
            content = {
                Box(
                    Modifier
                        .width(width)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                ) { content() }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val panel = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val margin = MARGIN.roundToPx()
            val gap = GAP.roundToPx()
            val x: Int
            val y: Int
            if (anchor == null) {
                x = (constraints.maxWidth - panel.width) / 2
                y = margin
            } else {
                x =
                    anchor.left.toInt().coerceIn(
                        margin,
                        (constraints.maxWidth - panel.width - margin).coerceAtLeast(margin),
                    )
                val below = anchor.bottom.toInt() + gap
                y = if (below + panel.height <= constraints.maxHeight - margin) {
                    below
                } else {
                    (anchor.top.toInt() - gap - panel.height).coerceAtLeast(margin)
                }
            }
            layout(constraints.maxWidth, constraints.maxHeight) { panel.place(IntOffset(x, y)) }
        }
    }
}

/**
 * The menu of a tile: a small title and the entries, under the tile. An entry may carry a hint
 * on the right ("◀ ▶", where the app is now). Choosing one closes the menu and runs it.
 */
@Composable
fun AnchoredMenu(
    title: String,
    entries: List<MenuEntry>,
    anchor: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val guard = remember { FreshPressGuard() }
    val first = remember { FocusRequester() }
    AnchoredPanel(anchor = anchor, width = MENU_WIDTH, onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.onPreviewKeyEvent(guard::swallow).padding(9.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, top = 3.dp, bottom = 6.dp),
            )
            entries.forEachIndexed { index, entry ->
                MenuRow(
                    entry = entry,
                    onClick = {
                        onDismiss()
                        entry.onChoose()
                    },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** One entry of an [AnchoredMenu]; [hint] shows on the right, dimmer. */
class MenuEntry(val title: String, val hint: String? = null, val onChoose: () -> Unit)

@Composable
private fun MenuRow(entry: MenuEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = scheme.surface,
            contentColor = scheme.onSurface,
            focusedContainerColor = scheme.inverseSurface,
            focusedContentColor = scheme.inverseOnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
            entry.hint?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val MENU_WIDTH = 280.dp
private val MARGIN = 24.dp
private val GAP = 14.dp
