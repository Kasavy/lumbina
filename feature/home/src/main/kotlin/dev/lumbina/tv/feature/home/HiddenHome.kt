package dev.lumbina.tv.feature.home

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * While [hidden], the d-pad can't walk into this from the screen shown over it. Should that
 * screen have lost the focus (the row it was on went away), the next arrow would look for it here
 * and find nothing to stop on: then [over], the screen on top, takes it.
 *
 * Its semantics are gone meanwhile: the screen over it is drawn in the same window, and a mouse,
 * the phone's touchpad or a screen reader would otherwise reach through that screen's empty
 * places (a panel's heading) to the tile or button under them.
 */
internal fun Modifier.hiddenUnder(hidden: Boolean, over: ScreenOnTop?): Modifier = focusProperties {
    onEnter = {
        if (hidden) {
            if (over == null || over.hasFocus) cancelFocusChange() else over.focus.requestFocus()
        }
    }
}.focusGroup().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)

/** The screen shown over home, as far as the focus goes: where it is, and whether it has the focus now. */
class ScreenOnTop {
    val focus = FocusRequester()
    var hasFocus = false
        private set

    /** For the container of the screens shown over home. */
    val area: Modifier = Modifier.focusRequester(focus).onFocusChanged { hasFocus = it.hasFocus }.focusGroup()
}
