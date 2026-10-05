package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The phone touchpad's pointer: an arrow drawn over everything, moved by the page's deltas. A tap
 * clicks the element under it and a two-finger swipe scrolls the list under it, through the
 * accessibility tree: some firmware restarts an accessibility service that injects touch
 * gestures, and TV apps answer tree actions anyway. It hides after a few idle seconds. Main thread.
 */
internal class PhoneCursor(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val width get() = service.resources.displayMetrics.widthPixels
    private val height get() = service.resources.displayMetrics.heightPixels
    private var x = -1f
    private var y = -1f

    fun move(dx: Float, dy: Float) {
        if (x < 0) {
            x = width / 2f
            y = height / 2f
        }
        x = (x + dx * SPEED).coerceIn(0f, width - 1f)
        y = (y + dy * SPEED).coerceIn(0f, height - 1f)
        show()
    }

    /** Clicks the element under the pointer (a long click when [long]). */
    fun click(long: Boolean): Boolean {
        if (x < 0) return false
        show()
        val action = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        val target = nodeAt(x.toInt(), y.toInt()) { if (long) it.isLongClickable else it.isClickable }
            ?: return false
        return target.performAction(action)
    }

    /** Scrolls the list under the pointer; a finger moving up ([dy] < 0) scrolls forward, as on a touchscreen. */
    fun scroll(dy: Float): Boolean {
        val px = if (x < 0) width / 2 else x.toInt()
        val py = if (x < 0) height / 2 else y.toInt()
        val target = nodeAt(px, py) { it.isScrollable } ?: return false
        val action = when {
            dy < 0 -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return target.performAction(action)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        view?.let(windowManager::removeView)
        view = null
        params = null
    }

    /** The innermost element at ([px], [py]) that [accepts], in the topmost window that has one. */
    private fun nodeAt(px: Int, py: Int, accepts: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val roots = service.windows.sortedByDescending { it.layer }.mapNotNull { it.root }
            .ifEmpty { listOfNotNull(service.rootInActiveWindow) }
        return roots.firstNotNullOfOrNull { deepest(it, px, py, accepts) }
    }

    private fun deepest(
        node: AccessibilityNodeInfo,
        px: Int,
        py: Int,
        accepts: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val bounds = Rect().also(node::getBoundsInScreen)
        if (!bounds.contains(px, py) || !node.isVisibleToUser) return null
        val inner = (0 until node.childCount).asSequence()
            .mapNotNull { node.getChild(it) }
            .firstNotNullOfOrNull { deepest(it, px, py, accepts) }
        return inner ?: node.takeIf(accepts)
    }

    private fun show() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, HIDE_AFTER_MILLIS)
        val layout = params ?: create()
        layout.x = x.toInt()
        layout.y = y.toInt()
        view?.let { windowManager.updateViewLayout(it, layout) }
    }

    private fun create(): WindowManager.LayoutParams {
        val size = service.dp(ARROW_DP)
        val arrow = ArrowView(service)
        val layout = overlayParams(
            width = size,
            height = size,
            gravity = Gravity.TOP or Gravity.START,
            extraFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        if (!windowManager.addOverlay(arrow, layout)) return layout
        view = arrow
        params = layout
        return layout
    }

    /** A white arrow with a dark outline, its tip at the top-left corner (the pointed spot). */
    private class ArrowView(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FILL }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE
            style = Paint.Style.STROKE
            strokeWidth = OUTLINE_WIDTH
        }

        private val arrow = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            arrow.rewind()
            arrow.moveTo(0f, 0f)
            arrow.lineTo(0f, h * TIP_LENGTH)
            arrow.lineTo(w * NOTCH_X, h * NOTCH_Y)
            arrow.lineTo(w * WING_X, h * WING_Y)
            arrow.close()
            canvas.drawPath(arrow, fill)
            canvas.drawPath(arrow, outline)
        }

        private companion object {
            const val FILL = 0xFFFFFFFF.toInt()
            const val OUTLINE = 0xFF101010.toInt()
            const val OUTLINE_WIDTH = 3f
            const val TIP_LENGTH = 0.9f
            const val NOTCH_X = 0.28f
            const val NOTCH_Y = 0.66f
            const val WING_X = 0.66f
            const val WING_Y = 0.62f
        }
    }

    private companion object {
        const val SPEED = 2.2f
        const val HIDE_AFTER_MILLIS = 5_000L
        const val ARROW_DP = 32
    }
}
