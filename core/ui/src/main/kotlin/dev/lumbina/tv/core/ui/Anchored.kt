package dev.lumbina.tv.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * Where the focused tile is on screen, so the keyboard that names it can open right under it.
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
                        .dialogSurface(),
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
 * The menu of a tile, in the [SidePanel]: the entries, each under the caption of its
 * [MenuEntry.group] when that differs from the entry before. An entry may carry a hint on the right
 * ("◀ ▶", where the app is now). Choosing one runs it, then closes the menu ([onDismiss]); Back
 * goes [onBack], by default the same. [focused]: the entry the focus starts on, the first when
 * there is none of that title (one come back to, [onChoose] told which). A list longer than the
 * panel (large text) scrolls to keep the focused entry in view.
 */
@Composable
fun TileMenu(
    title: String,
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onDismiss,
    subtitle: String? = null,
    path: List<String> = emptyList(),
    focused: String? = null,
    onChoose: (String) -> Unit = {},
) {
    val first = remember { FocusRequester() }
    val start = entries.indexOfFirst { it.title == focused }.coerceAtLeast(0)
    SidePanel(title = title, onDismiss = onBack, modifier = modifier, path = path, subtitle = subtitle) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            entries.forEachIndexed { index, entry ->
                val group = entry.group
                if (group != null && group != entries.getOrNull(index - 1)?.group) PanelGroup(group)
                MenuRow(
                    entry = entry,
                    // Chosen first: one that leads on to a page opens it, and the closing after leaves it be.
                    onClick = {
                        onChoose(entry.title)
                        entry.onChoose()
                        if (!entry.staysOpen) onDismiss()
                    },
                    modifier = (if (index == start) Modifier.focusRequester(first) else Modifier)
                        .padding(start = if (entry.nested) 24.dp else 0.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocusSoon() }
}

/**
 * One entry of a [TileMenu]; [hint] shows on the right, dimmer; [group] is the caption it stands
 * under; a [nested] one belongs to the entry above it (a folder of a row) and is set in. One that
 * [staysOpen] leads on within the menu instead of closing it.
 */
class MenuEntry(
    val title: String,
    val hint: String? = null,
    val group: String? = null,
    val nested: Boolean = false,
    val staysOpen: Boolean = false,
    val onChoose: () -> Unit,
)

@Composable
private fun MenuRow(entry: MenuEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PanelRow(entry.title, onClick, modifier, entry.hint)
}

/** One row of a [SidePanel]'s list: a [title] and, on the right and dimmer, a [hint] (a tick, a value). */
@Composable
fun PanelRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(13.dp)),
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            hint?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val MARGIN = 24.dp
private val GAP = 14.dp
