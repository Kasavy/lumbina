package dev.lumbina.tv.core.domain.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns raw key events into gestures and actions.
 *
 * - Keys the policy doesn't handle are not consumed (the system sees them).
 * - A handled key is consumed on both down and up, so the firmware never acts on it.
 * - Auto-repeat downs while a key is held are ignored.
 * - [KeyGesture.Down] fires immediately (push-to-talk). On release, a press shorter than
 *   [tapMillis] fires [KeyGesture.Tap] (plus [KeyGesture.DoubleTap] if another tap ended
 *   within [doubleTapMillis]) when bound; otherwise [KeyGesture.Up] fires.
 * - With a long-press binding, the action fires after [longPressMillis] of holding; releasing
 *   earlier fires the short-press action. A long press never also fires the short press.
 * - With a [KeyGesture.DoubleTap] but no [KeyGesture.Tap] binding (e.g. Home), a short press
 *   waits [doublePressMillis] for a second one: if it comes, only the double tap fires.
 * - Keys are tracked independently, e.g. Home pressed while Mic is held.
 */
class RemoteKeyInterpreter(
    private val keyMap: KeyMap,
    /** Replaceable, e.g. once voice input becomes available; presses in progress carry on. */
    var policy: KeyBindingPolicy,
    private val scope: CoroutineScope,
    private val onAction: (RemoteAction) -> Unit,
    private val now: () -> Long,
    private val longPressMillis: Long = DEFAULT_LONG_PRESS_MILLIS,
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
        val key = keyMap.map(raw.scanCode, raw.keyCode) ?: return false
        if (!policy.handles(key)) return false

        return when (raw.action) {
            KeyAction.Down -> {
                onDown(key)
                true
            }

            KeyAction.Up -> onUp(key)
        }
    }

    private fun onDown(key: RemoteKey) {
        if (key in held) return // auto-repeat
        val hold = Hold(downAt = now())
        held[key] = hold
        pendingShort.remove(key)?.let { first ->
            first.cancel()
            secondPress += key
        }
        policy.actionFor(key, KeyGesture.Down)?.let(onAction)
        if (policy.hasLongPress(key)) {
            hold.longPressJob = scope.launch {
                delay(longPressMillis)
                hold.longPressFired = true
                policy.actionFor(key, KeyGesture.LongPress)?.let(onAction)
            }
        }
    }

    private fun onUp(key: RemoteKey): Boolean {
        val hold = held.remove(key) ?: return false
        hold.longPressJob?.cancel()
        val t = now()
        val tap = policy.actionFor(key, KeyGesture.Tap)
        if (tap != null && t - hold.downAt < tapMillis) {
            onAction(tap)
            val previous = lastTapAt.remove(key)
            if (previous != null && t - previous <= doubleTapMillis) {
                policy.actionFor(key, KeyGesture.DoubleTap)?.let(onAction)
            } else {
                lastTapAt[key] = t
            }
        } else {
            policy.actionFor(key, KeyGesture.Up)?.let(onAction)
        }
        val second = secondPress.remove(key)
        if (hold.longPressFired) return true
        val double = policy.actionFor(key, KeyGesture.DoubleTap)
        val short = policy.actionFor(key, KeyGesture.ShortPress)
        when {
            tap != null || double == null -> short?.let(onAction)

            second -> onAction(double)

            else -> pendingShort[key] = scope.launch {
                delay(doublePressMillis)
                pendingShort.remove(key)
                short?.let(onAction)
            }
        }
        return true
    }

    private companion object {
        const val DEFAULT_LONG_PRESS_MILLIS = 600L
        const val DEFAULT_TAP_MILLIS = 350L
        const val DEFAULT_DOUBLE_TAP_MILLIS = 600L
        const val DEFAULT_DOUBLE_PRESS_MILLIS = 300L
    }
}
