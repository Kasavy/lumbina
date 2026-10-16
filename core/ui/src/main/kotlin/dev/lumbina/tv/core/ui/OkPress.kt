package dev.lumbina.tv.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * One press of OK on something drawn without tv-material: a click when released, unless it was
 * held long enough to be a long click. It takes the keys before the element's own click sees them
 * ([okPress], ahead of the `clickable`): that click takes any OK going down for a press, a repeat
 * too, so OK still held after a long press opened a menu would click whatever the menu focused.
 */
class OkPress {
    private var down = false
    private var held = false

    /** Whether OK is down on the element now, before it counted as a long press: for the look of a press. */
    var pressed by mutableStateOf(false)
        private set

    fun handle(event: KeyEvent, onClick: (() -> Unit)?, onLongClick: (() -> Unit)?): Boolean {
        if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) return false
        when (event.type) {
            KeyEventType.KeyDown -> {
                val first = event.nativeKeyEvent.repeatCount == 0
                val long = down && !held && event.nativeKeyEvent.isLongPress
                if (first) {
                    down = true
                    held = false
                } else if (long && onLongClick != null) {
                    held = true
                    onLongClick()
                }
            }

            // Only a press that began here: OK held while the focus arrived isn't a click here.
            KeyEventType.KeyUp -> {
                if (down && !held) onClick?.invoke()
                down = false
            }
        }
        pressed = down && !held
        return true
    }

    /** Forgets a press, as when the focus leaves: its release, wherever it comes, is not a click here. */
    fun cancel() {
        down = false
        held = false
        pressed = false
    }
}

/** OK on this element goes through [ok] (see [OkPress]), and a press is forgotten when the focus leaves. */
fun Modifier.okPress(ok: OkPress, onClick: (() -> Unit)?, onLongClick: (() -> Unit)?): Modifier =
    onFocusChanged { if (!it.isFocused) ok.cancel() }.onPreviewKeyEvent { ok.handle(it, onClick, onLongClick) }
