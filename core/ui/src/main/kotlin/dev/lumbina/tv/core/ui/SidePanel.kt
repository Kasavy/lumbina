package dev.lumbina.tv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The panel everything about one app, folder, row or setting opens in: on the right, over the
 * screen it was opened from, which stays in sight (dimmed) and alive. [path] names the pages above
 * this one, nearest last. Back closes it; presses still down when it appears (OK held to open it) are
 * ignored. With [fitContent] it is only as tall as what it holds, from the top: for a short list that
 * comes and goes. It begins [LocalPanelTop] down, without dimming what it leaves in sight.
 */
@Composable
fun SidePanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    path: List<String> = emptyList(),
    subtitle: String? = null,
    width: Dp = PANEL_WIDTH,
    fitContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Read here: the window may be laid out elsewhere, with the locals of that place.
    val top = LocalPanelTop.current
    PanelWindow(onDismiss, modifier, dim = top == 0.dp) { guarded ->
        SidePanelSurface(
            title = title,
            modifier = guarded.align(if (fitContent) Alignment.TopEnd else Alignment.CenterEnd).padding(top = top),
            path = path,
            subtitle = subtitle,
            width = width,
            fitContent = fitContent,
            content = content,
        )
    }
}

/**
 * The panel itself, without a window of its own: for a screen that is already a layer over home
 * (the settings) and lays the panel out at its right edge. [keyHint], only where the keys do
 * something one would not guess (arrows, OK and Back mean the same everywhere), says what they do
 * here and stays at the bottom.
 */
@Composable
fun SidePanelSurface(
    title: String,
    modifier: Modifier = Modifier,
    path: List<String> = emptyList(),
    subtitle: String? = null,
    keyHint: String? = null,
    width: Dp = PANEL_WIDTH,
    fitContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    PanelBody(
        keyHint = keyHint,
        fill = !fitContent,
        padding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
        modifier = modifier
            .padding(PANEL_MARGIN)
            .width(width)
            .then(if (fitContent) Modifier else Modifier.fillMaxHeight()),
        heading = { PanelHeading(title, path.takeLast(PATH_LEVELS), subtitle) },
        content = content,
    )
}

/**
 * A window in the middle of the screen, over the same dimmed backdrop as [SidePanel]: for a grid
 * too wide for the panel. [titleEnd] stands at the right of the title, e.g. how many are chosen.
 * Back closes it; presses still down when it appears are ignored.
 */
@Composable
fun CenteredPanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleEnd: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PanelWindow(onDismiss, modifier) { guarded ->
        PanelBody(
            keyHint = null,
            fill = true,
            padding = PaddingValues(horizontal = 26.dp, vertical = 20.dp),
            modifier = guarded.align(Alignment.Center).size(CENTERED_WIDTH, CENTERED_HEIGHT),
            heading = {
                // The count on the title's line, not the subtitle's.
                Row {
                    Column(Modifier.weight(1f)) { PanelHeading(title, emptyList(), subtitle) }
                    titleEnd?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                    }
                }
            },
            content = content,
        )
    }
}

/**
 * A window over the dimmed screen, which Back closes: its own, or inside a [PanelHost] the host's.
 * [panel] goes in it with a modifier that ignores presses still down when it appears (OK held to
 * open it).
 */
@Composable
private fun PanelWindow(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dim: Boolean = true,
    panel: @Composable BoxScope.(guarded: Modifier) -> Unit,
) {
    val guard = remember { FreshPressGuard() }
    val window: @Composable () -> Unit = {
        Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dim) SCRIM_ALPHA else 0f))) {
            panel(Modifier.onPreviewKeyEvent(guard::swallow))
        }
    }
    val host = LocalPanelHost.current
    if (host != null) {
        HostedPanel(host, onDismiss, window)
    } else {
        Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) { window() }
    }
}

/**
 * The panel's card: [heading], [content] under it ([fill]ing the height left, or only as tall as
 * it is), and the [keyHint] at the bottom if there is one. [modifier] sizes it.
 */
@Composable
private fun PanelBody(
    keyHint: String?,
    fill: Boolean,
    padding: PaddingValues,
    heading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(PANEL_CORNER))
            .padding(padding),
    ) {
        // Its own text color: opened from inside a button, it would take the button's.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            heading()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f, fill = fill)) {
                content()
            }
        }
        if (keyHint != null) {
            Text(
                text = keyHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PanelHeading(title: String, path: List<String>, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        if (path.isNotEmpty()) {
            Text(
                text = path.joinToString(PATH_SEPARATOR),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A caption over a group of entries in a [SidePanel]. */
@Composable
fun PanelGroup(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
    )
}

/**
 * 880 px on a 1080p screen, for every menu and every page of the settings alike: a panel that
 * changes its width from page to page reads as jumping about.
 */
val PANEL_WIDTH = 440.dp

/**
 * How far down the side panels opened here begin, for what on the screen behind must stay in sight
 * over them (the top bar while its settings are open, see [ScreenMarks]); they leave it undimmed.
 */
@Suppress("CompositionLocalAllowlist")
val LocalPanelTop = compositionLocalOf { 0.dp }

/** 1360×880 px on a 1080p screen: room for a grid of app tiles, and home still in sight around it. */
private val CENTERED_WIDTH = 680.dp
private val CENTERED_HEIGHT = 440.dp

internal val PANEL_MARGIN = 20.dp
private val PANEL_CORNER = 22.dp

/** The path shows the parent and the page above it; a longer one would wrap. */
private const val PATH_LEVELS = 2
private const val PATH_SEPARATOR = "  ›  "
