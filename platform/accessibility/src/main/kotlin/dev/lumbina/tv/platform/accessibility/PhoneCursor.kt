package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import dev.lumbina.tv.core.common.LumbinaActions

/**
 * The phone touchpad's pointer: an arrow drawn over everything, moved by the page's deltas. A tap
 * clicks the element under it: in Lumbina's own overlays directly ([Overlays]), elsewhere through
 * the accessibility tree ([PointerTree]), as where [Touchpad] has no arrows a two-finger swipe
 * scrolls the list under it: some firmware restarts an accessibility service that injects touch
 * gestures, and TV apps answer tree actions anyway. It hides after a few idle seconds. Main thread.
 */
internal class PhoneCursor(private val service: AccessibilityService) : Pointer {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val width get() = service.resources.displayMetrics.widthPixels
    private val height get() = service.resources.displayMetrics.heightPixels
    private var x = -1f
    private var y = -1f

    override fun move(dx: Float, dy: Float) {
        if (x < 0) {
            x = width / 2f
            y = height / 2f
        }
        x = (x + dx * SPEED).coerceIn(0f, width - 1f)
        y = (y + dy * SPEED).coerceIn(0f, height - 1f)
        show()
        service.overlays?.pointedAt(x.toInt(), y.toInt())
    }

    /**
     * Clicks the element under the pointer (a long click when [long]), after focusing it so the
     * arrows go on from there. Lumbina's own overlays are looked at first, then the windows from the
     * topmost down: the first that covers the point is the one clicked, so a click on a panel's
     * empty place or a dialog's margin isn't passed to what lies under. A long click where nothing
     * takes one is a click, as holding OK is.
     */
    override fun click(long: Boolean): Boolean {
        if (x < 0) return false
        show()
        val px = x.toInt()
        val py = y.toInt()
        return service.overlays?.clickAt(px, py, long) ?: clickInWindows(px, py, long)
    }

    private fun clickInWindows(px: Int, py: Int, long: Boolean): Boolean {
        val root = topmostAt(px, py) ?: return false
        val tree = Nodes(px, py)
        val held = if (long) tree.deepest(root) { it.isLongClickable } else null
        if (held?.focusAnd(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true) return true
        tree.deepest(root) { it.isClickable }?.let { return it.focusAnd(AccessibilityNodeInfo.ACTION_CLICK) }
        // Lumbina's own screen also reacts to taps accessibility doesn't see: beside a panel (it
        // closes), on a panel's empty places (nothing happens), so it is asked to tap there itself.
        if (root.packageName != service.packageName) return false
        val tap = Intent(LumbinaActions.TAP_AT).setPackage(service.packageName)
            .putExtra(LumbinaActions.EXTRA_X, px)
            .putExtra(LumbinaActions.EXTRA_Y, py)
        service.sendBroadcast(tap)
        return true
    }

    private fun AccessibilityNodeInfo.focusAnd(action: Int): Boolean {
        if (isFocusable && !isFocused) performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return performAction(action)
    }

    /** The root of the topmost window that covers the point; the active window's where none says its bounds. */
    private fun topmostAt(px: Int, py: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        return service.windows.sortedByDescending { it.layer }
            .firstOrNull { window -> window.getBoundsInScreen(bounds).let { bounds.contains(px, py) } }
            ?.root
            ?: service.rootInActiveWindow
    }

    /**
     * Scrolls the list under the pointer the way [dir] says ("down": on to what is below), along
     * that axis only ([scrollTarget]); a list that doesn't say its axis is scrolled forward or back.
     */
    override fun scroll(dir: String): Boolean {
        // x < 0: the pointer was never moved, and stands nowhere yet.
        val px = if (x < 0) width / 2 else x.toInt()
        val py = if (x < 0) height / 2 else y.toInt()
        val along = SCROLLS[dir] ?: return false
        val target = Nodes(px, py).scrollTarget(
            roots(),
            along = { node -> node.actionList.any { it.id == along.id } },
            axisless = { node -> node.isScrollable && node.actionList.none { it.id in AXES } },
        ) ?: return false
        val forward = dir == "down" || dir == "right"
        return target.node.performAction(
            when {
                target is ScrollTarget.Along -> along.id
                forward -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            },
        )
    }

    override fun hide() {
        handler.removeCallbacks(hideRunnable)
        view?.let(windowManager::removeView)
        view = null
        params = null
        service.overlays?.keepOnTop(null)
    }

    /** Back above an overlay that came after it (the recent apps opened from the quick menu by a click). */
    private fun raise() {
        val shown = view ?: return
        val layout = params ?: return
        windowManager.removeView(shown)
        if (!windowManager.addOverlay(shown, layout, listed = false)) {
            view = null
            params = null
            service.overlays?.keepOnTop(null)
        }
    }

    /** The windows' roots, topmost first. */
    private fun roots(): List<AccessibilityNodeInfo> = service.windows.sortedByDescending { it.layer }
        .mapNotNull { it.root }
        .ifEmpty { listOfNotNull(service.rootInActiveWindow) }

    /** The accessibility tree at (px, py). */
    private class Nodes(private val px: Int, private val py: Int) : PointerTree<AccessibilityNodeInfo> {
        private val bounds = Rect()

        override fun hits(node: AccessibilityNodeInfo): Boolean {
            node.getBoundsInScreen(bounds)
            return bounds.contains(px, py) && node.isVisibleToUser
        }

        override fun childCount(node: AccessibilityNodeInfo) = node.childCount

        override fun child(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? = node.getChild(index)

        /** Back to the pool below Android 13: a walk per scroll step would otherwise make one node per element. */
        @Suppress("DEPRECATION")
        override fun release(node: AccessibilityNodeInfo) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) node.recycle()
        }
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
        if (!windowManager.addOverlay(arrow, layout, listed = false)) return layout
        view = arrow
        params = layout
        service.overlays?.keepOnTop(arrow, ::raise)
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

        val SCROLLS = mapOf(
            "up" to AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP,
            "down" to AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN,
            "left" to AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT,
            "right" to AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT,
        )

        /** The actions of a list that says its axis. */
        val AXES = SCROLLS.values.map { it.id }.toSet()
        const val HIDE_AFTER_MILLIS = 5_000L
        const val ARROW_DP = 32
    }
}
