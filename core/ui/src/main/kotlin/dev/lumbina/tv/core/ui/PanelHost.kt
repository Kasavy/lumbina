package dev.lumbina.tv.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * One window for the panels [content] shows one after another, as a tile's menu and a page an entry
 * of it leads to: the screen under them does not show between the two, as it would while a window
 * of the next panel's own came up. One panel at a time. A panel in it sees the composition locals
 * of the host's place, as a popup sees those of its own.
 */
@Composable
fun PanelHost(content: @Composable () -> Unit) {
    val host = remember { PanelHostState() }
    CompositionLocalProvider(LocalPanelHost provides host, content = content)
    host.shown?.let { panel ->
        Popup(onDismissRequest = { panel.dismiss() }, properties = PopupProperties(focusable = true)) {
            val focus = LocalFocusManager.current
            SideEffect { host.clearFocus = { focus.clearFocus(force = true) } }
            var within by remember { mutableStateOf(false) }
            Box(Modifier.onFocusChanged { within = it.hasFocus }) {
                // Each panel its own state: one must not take over the last one's remembered focus.
                key(panel) { panel.content() }
            }
            // A panel that asks for no focus of its own gets the first thing in it, as in a window of its own.
            LaunchedEffect(panel) {
                repeat(FOCUS_FRAMES + 1) { withFrameNanos { } }
                if (!within) focus.moveFocus(FocusDirection.Next)
            }
        }
    }
}

/** The panel a [PanelHost] shows now, put there by [HostedPanel]. */
internal class PanelHostState {
    var shown by mutableStateOf<HostedPanel?>(null)

    /**
     * Takes the focus off the panel going away. Its focused entry, gone a frame later, would take
     * the focus with it after the next panel had asked for its own, and leave nothing focused.
     */
    var clearFocus: () -> Unit = {}
}

internal class HostedPanel(val dismiss: () -> Unit) {
    var content: @Composable () -> Unit by mutableStateOf({})
}

@Suppress("CompositionLocalAllowlist")
internal val LocalPanelHost = staticCompositionLocalOf<PanelHostState?> { null }

/**
 * Shows [content] in the window of [host] for as long as this is composed. Run where a panel
 * would open a window: the one going away gives the window up before the next takes it.
 */
@Composable
internal fun HostedPanel(host: PanelHostState, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val panel = remember { HostedPanel { dismiss() } }
    SideEffect { panel.content = content }
    DisposableEffect(panel) {
        host.shown = panel
        onDispose {
            // Only while the window is still this panel's: the next one's focus is not to be touched.
            if (host.shown === panel) {
                host.shown = null
                host.clearFocus()
            }
        }
    }
}

/**
 * Asks for the focus until it is given, for a few frames; false if it never was. A panel shown in
 * a [PanelHost]'s window is laid out a frame after it is composed, and a request before that is
 * refused; in a window of its own the window's first focus covered for that.
 */
suspend fun FocusRequester.requestFocusSoon(): Boolean {
    repeat(FOCUS_FRAMES) {
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
        withFrameNanos { }
    }
    return false
}

private const val FOCUS_FRAMES = 5
