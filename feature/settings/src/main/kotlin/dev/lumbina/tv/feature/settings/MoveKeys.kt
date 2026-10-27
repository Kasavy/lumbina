package dev.lumbina.tv.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * The keys of a row being moved, taken while it moves. OK and Back act when released after a press
 * made while moving, every other key when pressed. Otherwise the release of the OK held to pick
 * the row up would put it straight down, and the release of the OK that puts it down would reach
 * the row as a click once it no longer moves. The release of a key pressed before the move (that
 * held OK's above all) goes on to the row: kept from it, the row would stay pressed and take the
 * next OK for its end.
 */
internal class MoveKeys {
    /** The keys pressed while moving and not yet released. */
    private val pressed = mutableSetOf<Key>()

    /** Whether the event is consumed: all but a release whose press came before the row moved. */
    fun handle(type: KeyEventType, key: Key, repeatCount: Int, act: (Key) -> Unit): Boolean {
        when (type) {
            KeyEventType.KeyDown -> if (repeatCount == 0) {
                pressed += key
                if (key !in ON_RELEASE) act(key)
            }

            KeyEventType.KeyUp -> {
                if (!pressed.remove(key)) return false
                if (key in ON_RELEASE) act(key)
            }
        }
        return true
    }

    private companion object {
        val ON_RELEASE = setOf(Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape)
    }
}
