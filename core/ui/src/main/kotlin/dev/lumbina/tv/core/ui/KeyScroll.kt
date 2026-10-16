package dev.lumbina.tv.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Up and down scroll [state] while there is more that way, before they move the focus on. Put
 * around a text that may not fit and buttons that stay in view: the keys read the text first.
 */
@Composable
fun Modifier.keyScroll(state: ScrollState): Modifier {
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { KEY_SCROLL_STEP.toPx() }
    return onPreviewKeyEvent { state.scrollOn(it, step, scope) }
}

/** True when the key scrolled, or would have on its press: its release is taken too. */
private fun ScrollState.scrollOn(event: KeyEvent, step: Float, scope: CoroutineScope): Boolean {
    val by = when (event.key) {
        Key.DirectionDown -> step.takeIf { canScrollForward }
        Key.DirectionUp -> (-step).takeIf { canScrollBackward }
        else -> null
    } ?: return false
    if (event.type == KeyEventType.KeyDown) scope.launch { animateScrollBy(by) }
    return true
}

/** How far one press of up or down scrolls. */
private val KEY_SCROLL_STEP = 120.dp
