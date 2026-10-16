package dev.lumbina.tv.core.ui

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

/**
 * Swallows key events until a fresh press. A menu opened by holding OK appears while OK is
 * still down: the remote's repeats and the release would otherwise land on the menu's first
 * item and activate it. Use with `Modifier.onPreviewKeyEvent(guard::swallow)` on the dialog.
 */
class FreshPressGuard {
    private var armed = false

    /** True (consumed) until the first key down that is not a repeat. */
    fun swallow(event: KeyEvent): Boolean {
        if (armed) return false
        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
            armed = true
            return false
        }
        return true
    }
}
