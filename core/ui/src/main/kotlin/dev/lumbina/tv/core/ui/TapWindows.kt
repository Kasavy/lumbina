package dev.lumbina.tv.core.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Lumbina's dialogs and popups as they show, each a window of its own over the screen, so a tap
 * told from outside ([tapAt]) reaches the one on top: the phone touchpad's click where
 * accessibility finds nothing to click. Main thread.
 */
class TapWindows {
    private val shown = mutableListOf<View>()

    internal fun added(root: View) {
        shown.remove(root)
        shown += root
    }

    internal fun removed(root: View) {
        shown.remove(root)
    }

    /**
     * A touch down and up at the screen point ([x], [y]) in the topmost window, [screen] (the
     * screen's own window) when none shows: what a finger's tap there would do. Beside a dialog or
     * popup too: they hold the touch, as they hold the keys, and close at a tap beside them.
     */
    fun tapAt(x: Int, y: Int, screen: View) {
        val root = shown.lastOrNull { it.isAttachedToWindow } ?: screen
        val at = IntArray(2).also(root::getLocationOnScreen)
        val localX = (x - at[0]).toFloat()
        val localY = (y - at[1]).toFloat()
        val time = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time, action, localX, localY, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            root.dispatchTouchEvent(event)
            event.recycle()
        }
    }
}

// A local: a dialog or popup is a composition of its own, and only locals reach it from where it was opened.
@Suppress("CompositionLocalAllowlist")
val LocalTapWindows = staticCompositionLocalOf<TapWindows?> { null }

/** This window (a dialog's or popup's) is in the [TapWindows] while it shows. */
@Composable
internal fun ListedForTaps() {
    val windows = LocalTapWindows.current ?: return
    val root = LocalView.current.rootView
    DisposableEffect(windows, root) {
        windows.added(root)
        onDispose { windows.removed(root) }
    }
}
