package dev.lumbina.tv.platform.accessibility

import android.os.Handler
import android.view.KeyEvent
import android.view.ViewConfiguration

/**
 * A held arrow in an overlay goes on by itself, at the pace the system repeats a held key in apps,
 * so the overlay scrolls as Lumbina's own lists do. The service gets the remote's press and
 * release only, never those repeats. It stops on the key's release, or once [showing] is false:
 * a release can go elsewhere (to an overlay that came up over this one).
 */
internal class HeldArrow(
    private val handler: Handler,
    private val showing: () -> Boolean,
    private val timing: Timing = Timing(),
    private val step: (key: Int, repeated: Boolean) -> Unit,
) {
    /** How long a key is held before it repeats, then how often. */
    class Timing(
        val firstMillis: Long = ViewConfiguration.getKeyRepeatTimeout().toLong(),
        val everyMillis: Long = ViewConfiguration.getKeyRepeatDelay().toLong(),
    )

    private var held: Int? = null
    private val repeat = object : Runnable {
        override fun run() {
            val key = held ?: return
            if (!showing()) return letGo()
            step(key, true)
            if (held == key) handler.postDelayed(this, timing.everyMillis)
        }
    }

    val isHeld: Boolean get() = held != null

    /**
     * An arrow's press or release. A device that repeats a held key by itself sends repeats too:
     * the repeat here already runs, they do nothing.
     */
    fun onKey(event: KeyEvent) {
        when {
            event.action == KeyEvent.ACTION_UP -> letGo(event.keyCode)
            event.repeatCount == 0 -> press(event.keyCode)
        }
    }

    /** [key] went down: one step now, then more until it is let go. */
    fun press(key: Int) {
        letGo()
        held = key
        step(key, false)
        // The step may have closed the overlay, letting go.
        if (held == key) handler.postDelayed(repeat, timing.firstMillis)
    }

    /** Stops the repeat; with [key], only if that is the key held (another one's release leaves it). */
    fun letGo(key: Int? = null) {
        if (key != null && key != held) return
        held = null
        handler.removeCallbacks(repeat)
    }
}
