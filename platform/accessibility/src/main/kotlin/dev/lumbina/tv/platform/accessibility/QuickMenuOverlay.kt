package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The side panel at the right edge, over whatever is on screen, looking as the launcher's own
 * panels do: ▲ ▼ pick, ◀ ▶ change a value, OK acts, Back closes; left alone it closes by itself.
 * [Item.secondary] items come last, under a line. An item may open a [Page] in place of the list;
 * Back then returns to the list. It never takes focus — the service feeds it the remote's keys —
 * so a video underneath keeps playing.
 */
internal class QuickMenuOverlay(private val context: Context, private val texts: Texts) {

    /**
     * The panel's own words: its [title], and what the keys do on a page, where Back goes back to the
     * list. In the list the keys do what they do everywhere: nothing is said.
     */
    class Texts(val title: String, val pageKeys: String)

    /** One row: [value] is shown on the right and read again after every change. */
    class Item(
        val label: String,
        val value: () -> String? = { null },
        val onAdjust: ((Int) -> Unit)? = null,
        val closes: Boolean = true,
        val secondary: Boolean = false,
        /** What OK opens inside the panel; null from it: OK acts as [onOk]. */
        val page: () -> Page? = { null },
        val onOk: () -> Unit,
    )

    /** A page inside the panel: a picture to point a phone at, and what to read under it. */
    class Page(val title: String, val image: Bitmap, val lines: List<String>)

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val views = QuickMenuViews(context)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, TICK_MILLIS)
        }
    }
    private var root: FrameLayout? = null
    private var rows: List<LinearLayout> = emptyList()
    private var values: List<TextView> = emptyList()
    private var items: List<Item> = emptyList()
    private var selected = 0
    private var page: Page? = null

    val isShowing: Boolean get() = root != null

    fun show(items: List<Item>) {
        hide()
        this.items = items
        selected = 0
        val params = overlayParams(views.windowWidth, MATCH_PARENT, Gravity.END or Gravity.TOP)
        root = views.frame().takeIf { windowManager.addOverlay(it, params) } ?: return
        render()
        // What counts down (the sleep timer's minutes) goes on counting while the menu shows.
        handler.postDelayed(tick, TICK_MILLIS)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(tick)
        root?.let(windowManager::removeView)
        root = null
        rows = emptyList()
        values = emptyList()
        items = emptyList()
        page = null
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
            // From a page back to the list, from the list out.
            event.isBack && down -> Unit

            event.isBack && page == null -> hide()

            event.isBack -> {
                page = null
                render()
            }

            event.keyCode !in ARROWS && !event.isOk -> return false

            // A page has nothing to pick or press: the arrows and OK just stay with the panel.
            page != null -> Unit

            event.isOk -> if (!down) activate()

            down -> move(event.keyCode)
        }
        return true
    }

    private fun move(key: Int) {
        when (key) {
            KeyEvent.KEYCODE_DPAD_UP -> select(selected - 1)

            KeyEvent.KEYCODE_DPAD_DOWN -> select(selected + 1)

            else -> {
                items.getOrNull(selected)?.onAdjust?.invoke(if (key == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                refresh()
            }
        }
    }

    private fun activate() {
        val item = items.getOrNull(selected) ?: return
        val opens = item.page()
        if (opens != null) {
            page = opens
            return render()
        }
        if (item.closes) hide()
        item.onOk()
        if (!item.closes) refresh()
    }

    /**
     * A page stays longer: it is there to be read from a phone, which takes a while. Not for good:
     * the phone remote's code on it is not to be left over a video nobody watches.
     */
    private fun keepOpen() {
        handler.removeCallbacks(hideRunnable)
        if (isShowing) handler.postDelayed(hideRunnable, if (page == null) AUTO_HIDE_MILLIS else PAGE_HIDE_MILLIS)
    }

    private fun select(index: Int) {
        if (rows.isEmpty()) return
        selected = index.coerceIn(0, rows.lastIndex)
        rows.forEachIndexed { i, row -> views.mark(row, on = i == selected) }
    }

    /** Lays the panel out anew: the list, or the page opened from it. */
    private fun render() {
        val frame = root ?: return
        val shown = page
        val list = if (shown == null) views.list(items) else null
        rows = list?.rows.orEmpty()
        values = list?.values.orEmpty()
        val panel = if (shown == null) {
            views.panel(texts.title, above = null, body = list?.view ?: return, keys = null)
        } else {
            views.panel(shown.title, above = texts.title, body = views.page(shown), keys = texts.pageKeys)
        }
        frame.removeAllViews()
        frame.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        refresh()
        select(selected)
        keepOpen()
    }

    private companion object {
        const val AUTO_HIDE_MILLIS = 15_000L
        const val PAGE_HIDE_MILLIS = 180_000L
        const val TICK_MILLIS = 5_000L
        val ARROWS = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}
