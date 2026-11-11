package dev.lumbina.tv.core.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A click of a mouse here, outside the panel over it, runs [close], as Back does: a dialog closes
 * so too. Only what a pointer clicks where nothing of the panel is ([keepsClicks] on the panel).
 */
fun Modifier.closedByClickOutside(close: () -> Unit): Modifier = pointerInput(close) { detectTapGestures { close() } }

/** A panel's own empty places keep a mouse's click, so it isn't taken for one outside the panel. */
fun Modifier.keepsClicks(): Modifier = pointerInput(Unit) { detectTapGestures { } }
