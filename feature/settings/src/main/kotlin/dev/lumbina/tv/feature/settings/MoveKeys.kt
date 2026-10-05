package dev.lumbina.tv.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * The keys of a row being moved, all taken while it moves. ▲ ▼ act when pressed; OK and Back
 * act when released after a press made while moving. Otherwise the release of the OK held to
 * pick the row up would put it straight down, and the release of the OK that puts it down would
 * reach the row as a click once it no longer moves.
 */
internal class MoveKeys {
    private var pressed: Key? = null

    /** Always true: the event is consumed. */
    fun handle(type: KeyEventType, key: Key, repeatCount: Int, act: (Key) -> Unit): Boolean {
        when (type) {
            KeyEventType.KeyDown -> if (repeatCount == 0) {
                if (key in ON_RELEASE) pressed = key else act(key)
            }

            KeyEventType.KeyUp -> if (key == pressed) {
                pressed = null
                act(key)
            }
        }
        return true
    }

    private companion object {
        val ON_RELEASE = setOf(Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape)
    }
}
