package dev.lumbina.tv.platform.accessibility

import android.os.SystemClock
import android.view.KeyEvent

/**
 * An overlay's own button pressed again closes it rather than opening it anew. The press closes it
 * first, as a key the overlay doesn't use, and goes on to that key's binding, which would open it:
 * that binding asks [consume] and opens nothing. Kept until the key's gesture is over: a while after
 * its last press or release (a long press fires while held, a short one on release, a double press
 * on the second), or until another key is pressed.
 */
internal class ClosedByKey(private val now: () -> Long = SystemClock::uptimeMillis) {
    private var key: Int? = null
    private var at = 0L

    /** A key the overlay doesn't use closed it. */
    fun closedBy(keyCode: Int) {
        key = keyCode
        at = now()
    }

    /** Every key while the overlay is not showing: what ends the closing key's gesture, or goes on with it. */
    fun seen(keyCode: Int, action: Int, repeatCount: Int) {
        val closing = key ?: return
        when {
            keyCode == closing -> at = now()
            action == KeyEvent.ACTION_DOWN && repeatCount == 0 -> key = null
        }
    }

    /** Whether the binding about to open the overlay belongs to the press that just closed it. */
    fun consume(): Boolean {
        val just = key != null && now() - at < GESTURE_MILLIS
        key = null
        return just
    }

    private companion object {
        /** Longer than the wait for a double press and a long press: the gesture's binding fires within it. */
        const val GESTURE_MILLIS = 1_000L
    }
}
