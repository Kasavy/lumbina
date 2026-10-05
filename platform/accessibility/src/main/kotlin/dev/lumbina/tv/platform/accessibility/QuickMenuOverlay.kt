package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A column of settings at the right edge, over whatever is on screen: ▲ ▼ pick, ◀ ▶ change a
 * value, OK acts, Back closes; it also closes by itself when left alone. [Item.secondary] items
 * come last, smaller, under a line. It never takes focus — the service feeds it the remote's
 * keys — so a video underneath keeps playing.
 */
internal class QuickMenuOverlay(private val context: Context, private val title: String, private val hint: String) {

    /** One row: [value] is shown on the right and read again after every change. */
    class Item(
        val label: String,
        val value: () -> String? = { null },
        val onAdjust: ((Int) -> Unit)? = null,
        val closes: Boolean = true,
        val secondary: Boolean = false,
        val onOk: () -> Unit,
    )

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var root: View? = null
    private var rows: List<LinearLayout> = emptyList()
    private var values: List<TextView> = emptyList()
    private var items: List<Item> = emptyList()
    private var selected = 0

    val isShowing: Boolean get() = root != null

    fun show(items: List<Item>) {
        hide()
        this.items = items
        selected = 0
        val params = overlayParams(context.dp(PANEL_WIDTH_DP), MATCH_PARENT, Gravity.END or Gravity.TOP)
        root = createView().takeIf { windowManager.addOverlay(it, params) } ?: return
        select(0)
        keepOpen()
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        root?.let(windowManager::removeView)
        root = null
        rows = emptyList()
        values = emptyList()
        items = emptyList()
    }

    /** Re-reads the values, e.g. after a setting changed. */
    fun refresh() {
        values.forEachIndexed { i, view ->
            val value = items[i].value()
            view.text = value.orEmpty()
            view.visibility = if (value == null) View.GONE else View.VISIBLE
        }
    }

    /**
     * Handles a key while showing; returns true if consumed. Another key pressed closes the
     * menu and is left to the caller (e.g. Home goes on to open Lumbina); the release and
     * auto-repeat of the Home hold that opened the menu are left alone.
     */
    fun onKey(event: KeyEvent): Boolean {
        if (!isShowing) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        if (!handle(event, down)) {
            if (down && event.repeatCount == 0) hide()
            return false
        }
        keepOpen()
        return true
    }

    /** False for keys the menu doesn't use. */
    private fun handle(event: KeyEvent, down: Boolean): Boolean {
        when {
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP -> if (down) select(selected - 1)
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> if (down) select(selected + 1)
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> if (down) adjust(-1)
            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) adjust(1)
            event.isOk -> if (!down) activate()
            event.isBack -> if (!down) hide()
            else -> return false
        }
        return true
    }

    private fun adjust(step: Int) {
        items.getOrNull(selected)?.onAdjust?.invoke(step)
        refresh()
    }

    private fun activate() {
        val item = items.getOrNull(selected) ?: return
        if (item.closes) hide()
        item.onOk()
        if (!item.closes) refresh()
    }

    private fun keepOpen() {
        handler.removeCallbacks(hideRunnable)
        if (isShowing) handler.postDelayed(hideRunnable, AUTO_HIDE_MILLIS)
    }

    private fun select(index: Int) {
        if (rows.isEmpty()) return
        selected = index.coerceIn(0, rows.lastIndex)
        rows.forEachIndexed { i, row ->
            val on = i == selected
            row.background = if (on) context.rounded(OverlayPalette.SELECTED, ROW_CORNER_DP) else null
            for (child in 0 until row.childCount) {
                val color = if (on) OverlayPalette.SELECTED_TEXT else OverlayPalette.TEXT
                (row.getChildAt(child) as? TextView)?.setTextColor(color)
            }
        }
    }

    private fun createView(): View {
        val rowPad = context.dp(ROW_PADDING_DP)
        val rowVertical = context.dp(ROW_VERTICAL_DP)
        val gap = context.dp(GAP_DP)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = context.dp(PANEL_PADDING_DP)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(OverlayPalette.PANEL)
        }
        panel.addView(context.label(title, TITLE_SP).apply { setPadding(rowPad, 0, 0, gap) })
        val made = items.mapIndexed { index, item ->
            if (item.secondary && items.getOrNull(index - 1)?.secondary == false) panel.addView(divider(gap))
            val value = context.label("", VALUE_SP).apply { gravity = Gravity.END }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(rowPad, rowVertical, rowPad, rowVertical)
                addView(
                    context.label(item.label, if (item.secondary) SECONDARY_SP else LABEL_SP),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(value)
            }
            panel.addView(row)
            row to value
        }
        rows = made.map { it.first }
        values = made.map { it.second }
        refresh()
        panel.addView(context.label(hint, HINT_SP).apply { setPadding(rowPad, gap * 2, 0, 0) })
        return panel
    }

    private fun divider(gap: Int) = View(context).apply {
        setBackgroundColor(OverlayPalette.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, context.dp(1)).apply {
            setMargins(context.dp(ROW_PADDING_DP), gap, context.dp(ROW_PADDING_DP), gap)
        }
    }

    private companion object {
        const val AUTO_HIDE_MILLIS = 15_000L
        const val PANEL_WIDTH_DP = 420
        const val PANEL_PADDING_DP = 28
        const val ROW_PADDING_DP = 18
        const val ROW_VERTICAL_DP = 12
        const val ROW_CORNER_DP = 12
        const val GAP_DP = 12
        const val TITLE_SP = 24f
        const val LABEL_SP = 18f
        const val SECONDARY_SP = 16f
        const val VALUE_SP = 16f
        const val HINT_SP = 13f
    }
}
