package dev.lumbina.tv.feature.home

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * [latest] while [visible]; hidden, what was shown last. So home takes changes in when it comes
 * back: a switch flipped in settings doesn't rebuild the rows behind it.
 */
@Composable
internal fun <T> heldWhileHidden(latest: T, visible: Boolean): T {
    var held by remember { mutableStateOf(latest) }
    if (visible) SideEffect { held = latest }
    return if (visible) latest else held
}

/**
 * While [hidden], the d-pad can't walk into this from the screen shown over it. Should that
 * screen have lost the focus (the row it was on went away), the next arrow would look for it here
 * and find nothing to stop on: then [over], the screen on top, takes it.
 */
internal fun Modifier.hiddenFromFocus(hidden: Boolean, over: ScreenOnTop?): Modifier = focusProperties {
    onEnter = {
        if (hidden) {
            if (over == null || over.hasFocus) cancelFocusChange() else over.focus.requestFocus()
        }
    }
}.focusGroup()

/** The screen shown over home, as far as the focus goes: where it is, and whether it has the focus now. */
class ScreenOnTop {
    val focus = FocusRequester()
    var hasFocus = false
        private set

    /** For the container of the screens shown over home. */
    val area: Modifier = Modifier.focusRequester(focus).onFocusChanged { hasFocus = it.hasFocus }.focusGroup()
}
