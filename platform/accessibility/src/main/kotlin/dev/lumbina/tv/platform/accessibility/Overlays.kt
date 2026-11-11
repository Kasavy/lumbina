package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * The service's own overlays as they show, owned by the service ([OverlayOwner]) and filled by
 * [addOverlay] and [removeOverlay]. Accessibility doesn't list these windows to the service (on
 * Vidda only the app's own window is listed while they show), so the phone touchpad's click looks
 * for what is under its pointer here first ([clickAt]). One of
 * them can be kept above the others ([keepOnTop]): the touchpad's pointer, which a window added
 * later would lie over. Main thread.
 */
class Overlays internal constructor() {
    private val shown = mutableListOf<View>()
    private var onTop: View? = null
    private var raise: () -> Unit = {}

    /** [view] stays above the overlays shown after it: [raise] puts it back on top. Null: none. */
    fun keepOnTop(view: View?, raise: () -> Unit = {}) {
        onTop = view
        this.raise = raise
    }

    fun added(view: View) {
        if (view === onTop) return
        shown.remove(view)
        shown += view
        if (onTop?.parent != null) raise()
    }

    fun removed(view: View) {
        shown.remove(view)
    }

    /** The touchpad's pointer moved to the screen point ([x], [y]): the overlay under it hears so. */
    fun pointedAt(x: Int, y: Int) {
        (topmostAt(x, y) as? PointerFrame)?.onPointer?.invoke()
    }

    /**
     * Clicks (or long-clicks) what is at the screen point ([x], [y]) in the topmost overlay that
     * covers it: null when none does, so the click goes on to the apps' windows; true when something
     * took it; false when the overlay covers the point with nothing to click there, so it isn't
     * passed to what lies under. A long click where nothing takes one is a click, as holding OK is.
     * The overlays it misses hear of a touch outside them, as from a mouse ([closedByClickOutside]).
     */
    fun clickAt(x: Int, y: Int, long: Boolean): Boolean? {
        val window = topmostAt(x, y)
        touchOutside(except = window, x, y)
        if (window == null) return null
        if (long) innermost(window, x, y) { it.isLongClickable }?.let { if (it.performLongClick()) return true }
        return innermost(window, x, y) { it.isClickable }?.performClick() ?: false
    }

    private fun touchOutside(except: View?, x: Int, y: Int) {
        val missed = shown.filter { it !== except && it.parent != null && !it.shows(x, y) }
        if (missed.isEmpty()) return
        val time = SystemClock.uptimeMillis()
        val outside = MotionEvent.obtain(time, time, MotionEvent.ACTION_OUTSIDE, x.toFloat(), y.toFloat(), 0)
        missed.forEach { it.dispatchTouchEvent(outside) }
        outside.recycle()
    }

    private fun topmostAt(x: Int, y: Int): View? =
        shown.asReversed().firstOrNull { it.parent != null && it.isShown && it.shows(x, y) }

    private fun innermost(view: View, x: Int, y: Int, accepts: (View) -> Boolean): View? {
        if (view.visibility != View.VISIBLE || !view.covers(x, y)) return null
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                innermost(view.getChildAt(index), x, y, accepts)?.let { return it }
            }
        }
        return view.takeIf(accepts)
    }

    private fun View.covers(x: Int, y: Int): Boolean {
        val at = IntArray(2).also(::getLocationOnScreen)
        return x in at[0] until at[0] + width && y in at[1] until at[1] + height
    }

    /** The screen point is on what this overlay shows, not in its window's margin ([holds]). */
    private fun View.shows(x: Int, y: Int): Boolean {
        val at = IntArray(2).also(::getLocationOnScreen)
        return holds((x - at[0]).toFloat(), (y - at[1]).toFloat())
    }
}

/**
 * An overlay's frame that hears a pointer over it before what is inside does: a mouse moving or
 * clicking there, the touchpad's pointer moving there ([Overlays.pointedAt]). An overlay that closes
 * by itself after a while without keys stays while it is pointed at.
 */
internal class PointerFrame(context: Context) : FrameLayout(context) {
    var onPointer: (() -> Unit)? = null

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        onPointer?.invoke()
        return super.dispatchHoverEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onPointer?.invoke()
        return super.dispatchTouchEvent(event)
    }
}

/** What owns the [Overlays]: the accessibility service. */
interface OverlayOwner {
    val overlays: Overlays
}

/** The [Overlays] of the service this context is (or wraps); null outside it (tests). */
internal val Context.overlays: Overlays?
    get() {
        var at: Context? = this
        while (at != null) {
            if (at is OverlayOwner) return at.overlays
            at = (at as? ContextWrapper)?.baseContext
        }
        return null
    }

/**
 * The point ([x], [y], in this view) is on what the view shows. A [PointerFrame] (the quick menu's,
 * the recent apps') shows its children, its padding being a margin around the panel; any other
 * view (a card, the voice plate, drawn with its own background) shows all of itself.
 */
internal fun View.holds(x: Float, y: Float): Boolean {
    val inside = x >= 0 && y >= 0 && x < width && y < height
    if (!inside || this !is PointerFrame || childCount == 0) return inside
    return (0 until childCount).map(::getChildAt).any {
        it.visibility == View.VISIBLE && x >= it.left && x < it.right && y >= it.top && y < it.bottom
    }
}
