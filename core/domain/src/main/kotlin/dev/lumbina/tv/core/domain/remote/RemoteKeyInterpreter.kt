package dev.lumbina.tv.core.domain.remote

import dev.lumbina.tv.core.domain.LONG_PRESS_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns raw key events into gestures and actions.
 *
 * - A key is Home or the mic where the device's profile says so, else the button its key code names.
 * - Keys the policy doesn't handle are not consumed (the system sees them); whether a key is
 *   handled is settled as it goes down ([inLumbina]), and its release follows its press.
 * - A handled key is consumed on both down and up, so the firmware never acts on it.
 * - A repeat follows its first press: taken if that was, passed on if that was. A new press of a key
 *   whose release never came starts anew.
 * - [KeyGesture.Down] fires immediately (push-to-talk). On release, a press shorter than
 *   [tapMillis] fires [KeyGesture.Tap] (plus [KeyGesture.DoubleTap] if another tap ended
 *   within [doubleTapMillis]) when bound; otherwise [KeyGesture.Up] fires.
 * - With a long-press binding, the action fires after [longPressMillis] of holding; releasing
 *   earlier fires the short-press action. A long press never also fires the short press.
 * - With a [KeyGesture.DoubleTap] but no [KeyGesture.Tap] binding (e.g. Home), a short press
 *   waits [doublePressMillis] for a second one: if it comes, only the double tap fires.
 * - Keys are tracked independently, e.g. Home pressed while Mic is held.
 * - A gesture that has an action is first offered to [probe]; taken there, the action is not done.
 */
class RemoteKeyInterpreter(
    private val keyMap: KeyMap,
    /** Replaceable, e.g. once voice input becomes available; presses in progress carry on. */
    var policy: KeyBindingPolicy,
    private val scope: CoroutineScope,
    private val onAction: (RemoteAction) -> Unit,
    private val now: () -> Long,
    /** Whether one of Lumbina's own screens is in front: some keys are Lumbina's only there. */
    private val inLumbina: () -> Boolean = { true },
    /** A button being tried in the settings: true keeps the gesture's action from being done. */
    private val probe: (RemoteKey, KeyGesture) -> Boolean = { _, _ -> false },
    private val longPressMillis: Long = LONG_PRESS_MILLIS,
    private val tapMillis: Long = DEFAULT_TAP_MILLIS,
    private val doubleTapMillis: Long = DEFAULT_DOUBLE_TAP_MILLIS,
    private val doublePressMillis: Long = DEFAULT_DOUBLE_PRESS_MILLIS,
) {
    private class Hold(val downAt: Long, var longPressJob: Job? = null, var longPressFired: Boolean = false)

    private val held = mutableMapOf<RemoteKey, Hold>()
    private val lastTapAt = mutableMapOf<RemoteKey, Long>()

    /** A short press waiting to see whether a second one makes it a double tap. */
    private val pendingShort = mutableMapOf<RemoteKey, Job>()
    private val secondPress = mutableSetOf<RemoteKey>()

    /**
     * Returns true if the event is consumed. An up without our down goes on to the firmware: it saw
     * the down (pressed before we started, or while the policy left the key alone), so it needs the up.
     */
    fun onKey(raw: RawKey): Boolean {
        val key = keyMap.map(raw.scanCode, raw.keyCode) ?: RemoteKey.of(raw.scanCode, raw.keyCode)
        return when (raw.action) {
            // A repeat goes where its press went, wherever the screen and the bindings went meanwhile.
            KeyAction.Down -> if (raw.repeat) key in held else pressed(key)

            KeyAction.Up -> onUp(key)
        }
    }

    private fun pressed(key: RemoteKey): Boolean {
        // Its release never came (the screen went off under it): this is a press of its own, and
        // one left to another app must not have its release taken for the old one's.
        held.remove(key)?.longPressJob?.cancel()
        return policy.handles(key, inLumbina).also { if (it) onDown(key) }
    }

    private fun onDown(key: RemoteKey) {
        val hold = Hold(downAt = now())
        held[key] = hold
        pendingShort.remove(key)?.let { first ->
            first.cancel()
            secondPress += key
        }
        fire(key, KeyGesture.Down)
        if (policy.hasLongPress(key)) {
            hold.longPressJob = scope.launch {
                delay(longPressMillis)
                hold.longPressFired = true
                fire(key, KeyGesture.LongPress)
            }
        }
    }

    private fun fire(key: RemoteKey, gesture: KeyGesture) {
        val action = policy.actionFor(key, gesture) ?: return
        if (!probe(key, gesture)) onAction(action)
    }

    private fun onUp(key: RemoteKey): Boolean {
        val hold = held.remove(key) ?: return false
        hold.longPressJob?.cancel()
        val t = now()
        val tap = policy.actionFor(key, KeyGesture.Tap)
        if (tap != null && t - hold.downAt < tapMillis) {
            fire(key, KeyGesture.Tap)
            val previous = lastTapAt.remove(key)
            if (previous != null && t - previous <= doubleTapMillis) {
                fire(key, KeyGesture.DoubleTap)
            } else {
                lastTapAt[key] = t
            }
        } else {
            fire(key, KeyGesture.Up)
        }
        val second = secondPress.remove(key)
        if (hold.longPressFired) return true
        when {
            tap != null || policy.actionFor(key, KeyGesture.DoubleTap) == null -> fire(key, KeyGesture.ShortPress)

            second -> fire(key, KeyGesture.DoubleTap)

            else -> pendingShort[key] = scope.launch {
                delay(doublePressMillis)
                pendingShort.remove(key)
                fire(key, KeyGesture.ShortPress)
            }
        }
        return true
    }

    private companion object {
        const val DEFAULT_TAP_MILLIS = 350L
        const val DEFAULT_DOUBLE_TAP_MILLIS = 600L
        const val DEFAULT_DOUBLE_PRESS_MILLIS = 300L
    }
}
