package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import dev.lumbina.tv.core.domain.LONG_PRESS_MILLIS
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId

/**
 * Recent apps along the bottom of whatever is on screen: ◀ ▶ pick, OK opens, Back closes; holding
 * OK closes the app instead where [show] says it may ([onClose]), and there "Close all" stands
 * beside the title, ▲ from any card and ▼ back to it ([onCloseAll], with the apps not already
 * closing). It also closes by itself when left alone. It never takes focus — the service feeds it
 * the remote's keys ([onKey]) — so the app underneath keeps running undisturbed. A held ◀ or ▶ goes
 * on by itself ([HeldArrow]).
 */
internal class AppSwitcherOverlay(
    private val context: Context,
    texts: () -> AppSwitcherViews.Texts,
    private val onPick: (App) -> Unit,
    private val onClose: (App) -> Unit,
    private val onCloseAll: (List<App>) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val views = AppSwitcherViews(context, texts)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private val arrow = HeldArrow(handler, showing = { isShowing }) { key, repeated ->
        // On "Close all" the arrows have no card to go to.
        if (atCloseAll) return@HeldArrow
        // Held, the ribbon keeps up with the selection rather than easing after it at every step.
        select(if (key == KeyEvent.KEYCODE_DPAD_LEFT) selected - 1 else selected + 1, smooth = !repeated)
        keepOpen()
    }
    private val pointer = PointerUse(context)
    private var root: View? = null
    private var ribbon: AppSwitcherViews.Ribbon? = null
    private var apps: List<App> = emptyList()
    private var selected = 0
    private var close = CloseApps.No

    /** "Close all" beside the title has the selection; [selected] is the card ▼ goes back to. */
    private var atCloseAll = false

    /** The apps Lumbina is closing now: their cards say so until the device answers. */
    private val closing = mutableSetOf<AppId>()

    /** OK went down while showing; a release without it (held from before) does nothing. */
    private var okDown = false

    /** OK was held past a long press: its release must not switch too. */
    private var held = false

    // Timed, not by auto-repeat: some remotes send no repeats while OK is held.
    private val longPress = Runnable {
        // "Close all" has no app to hold: it closes them all on the release, held or not.
        if (atCloseAll) return@Runnable
        held = true
        // Holding an arrow too: whichever app it went to by then is not the one meant.
        if (arrow.isHeld) return@Runnable
        apps.getOrNull(selected)?.takeUnless { it.id in closing }?.let { app ->
            closing += app.id
            select(selected)
            onClose(app)
        }
    }

    val isShowing: Boolean get() = root != null

    /** The card under the selection, or the one ▼ goes back to from "Close all". */
    val selectedIndex: Int get() = selected

    /** Whether "Close all" beside the title has the selection. */
    val closeAllSelected: Boolean get() = atCloseAll

    /**
     * Shows the ribbon, the selection on the card [initial], or on "Close all" where [toCloseAll]
     * and the ribbon has it. [keepKeys]: it is shown anew over itself (what it says about closing
     * changed, an app was closed), and an OK being held goes on being held.
     */
    fun show(
        apps: List<App>,
        initial: Int,
        close: CloseApps = CloseApps.No,
        keepKeys: Boolean = false,
        toCloseAll: Boolean = false,
    ) {
        if (keepKeys) removeWindow() else hide()
        this.apps = apps
        this.close = close
        closing.retainAll(apps.map { it.id }.toSet())
        val made = views.ribbon(apps, close)
        val start = RibbonStart.of(initial, apps.size, toCloseAll, hasCloseAll = made.closeAll != null)
        atCloseAll = start.onCloseAll
        selected = start.card
        val params = overlayParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM, touchable = true)
        // A pointer aimed at a card is no less a use of the ribbon than a key, and of the device.
        val frame = PointerFrame(context).apply {
            addView(made.view, MATCH_PARENT, WRAP_CONTENT)
            onPointer = {
                keepOpen()
                pointer.used()
            }
        }
        root = frame.takeIf { windowManager.addOverlay(it, params) } ?: return
        frame.closedByClickOutside { hide() }
        ribbon = made
        made.clicks(
            open = { app ->
                hide()
                onPick(app)
            },
            closeOne = { index: Int, app: App ->
                atCloseAll = false
                if (app.id !in closing) {
                    closing += app.id
                    onClose(app)
                }
                select(index)
                keepOpen()
            }.takeIf { close == CloseApps.Yes },
            closeAll = {
                atCloseAll = true
                closeAll()
                select(selected)
                keepOpen()
            },
        )
        // Straight to the card: shown anew after a close, the ribbon would slide in from its start.
        select(selected, smooth = false)
        keepOpen()
    }

    /** [app] was closed, or could not be: either way its card no longer says "closing". */
    fun closed(app: App) {
        closing -= app.id
        if (isShowing) select(selected)
    }

    fun hide() {
        arrow.letGo()
        handler.removeCallbacks(longPress)
        okDown = false
        held = false
        removeWindow()
    }

    private fun removeWindow() {
        handler.removeCallbacks(hideRunnable)
        root?.let(windowManager::removeOverlay)
        root = null
        ribbon = null
    }

    /** Hides and stops loading artwork; the overlay is not used again. */
    fun dispose() {
        hide()
        views.dispose()
    }

    /**
     * Handles a key while showing; returns true if consumed. Pressing any other key closes the
     * switcher and is left to the caller (e.g. Home goes on to open Lumbina); releases and
     * auto-repeats of the switcher's own keys held from before are taken and do nothing (an arrow's
     * release stops the arrow going on).
     */
    fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when {
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ->
                arrow.onKey(event)

            // ▲ to "Close all" beside the title, where the ribbon has it; ▼ back to the card it was on.
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                val up = event.keyCode == KeyEvent.KEYCODE_DPAD_UP
                if (down && ribbon?.closeAll != null && up != atCloseAll) {
                    atCloseAll = up
                    select(selected)
                }
            }

            event.isOk -> onOk(down, event.repeatCount)

            event.isBack -> if (!down) hide()

            else -> {
                if (down && event.repeatCount == 0) hide()
                return false
            }
        }
        keepOpen()
        return true
    }

    private fun onOk(down: Boolean, repeatCount: Int) {
        when {
            down && repeatCount == 0 -> {
                okDown = true
                // Where apps can't be closed, holding OK is OK.
                if (close == CloseApps.Yes) handler.postDelayed(longPress, LONG_PRESS_MILLIS)
            }

            down -> Unit

            !okDown -> Unit

            held -> {
                okDown = false
                held = false
            }

            atCloseAll -> {
                okDown = false
                handler.removeCallbacks(longPress)
                closeAll()
            }

            else -> {
                handler.removeCallbacks(longPress)
                val app = apps.getOrNull(selected)
                hide()
                app?.let(onPick)
            }
        }
    }

    /** Every card says "closing" until the device answers for its app; the ribbon stays. */
    private fun closeAll() {
        val left = apps.filterNot { it.id in closing }
        if (left.isEmpty()) return
        closing += left.map { it.id }
        select(selected)
        onCloseAll(left)
    }

    private fun keepOpen() {
        handler.removeCallbacks(hideRunnable)
        if (isShowing) handler.postDelayed(hideRunnable, AUTO_HIDE_MILLIS)
    }

    fun letGo(keyCode: Int) = arrow.letGo(keyCode)

    private fun select(index: Int, smooth: Boolean = true) {
        val shown = ribbon ?: return
        if (shown.cards.isEmpty()) return
        selected = index.coerceIn(0, shown.cards.lastIndex)
        shown.cards.forEachIndexed { i, card ->
            views.mark(card, on = !atCloseAll && i == selected, closing = card.app.id in closing)
        }
        // While all of them are closing, how many there were: "· 0" would read as nothing to close.
        val left = apps.count { it.id !in closing }.takeIf { it > 0 } ?: apps.size
        views.markCloseAll(shown, on = atCloseAll, count = left)
        val card = shown.cards.getOrNull(selected)?.frame ?: return
        val scroller = shown.scroller ?: return
        scroller.post {
            // The overlay may have closed in the meantime, taking the card off screen.
            val cell = card.parent as? View ?: return@post
            val x = edgeScroll(cell.left, cell.right, scroller.scrollX, scroller.width, views.edge) ?: return@post
            if (smooth) scroller.smoothScrollTo(x, 0) else scroller.scrollTo(x, 0)
        }
    }

    private companion object {
        const val AUTO_HIDE_MILLIS = 10_000L
    }
}

/**
 * Where the ribbon scrolls to show the card spanning [left] to [right] (scrolled to [scrollX], [width]
 * wide): as home's rows do, only when the card would come closer than [margin] to a side, and just
 * enough; null where it stays.
 */
internal fun edgeScroll(left: Int, right: Int, scrollX: Int, width: Int, margin: Int): Int? {
    val start = scrollX + margin
    val end = scrollX + width - margin
    val to = when {
        left < start -> left - margin
        right > end -> right - width + margin
        else -> return null
    }
    return to.coerceAtLeast(0)
}

/**
 * Where the selection stands as the ribbon shows: on [card] within the [cards] (the last one when
 * the card it was on is gone), and on "Close all" only where asked and the ribbon has it. A card
 * closed from the end never hands the selection to "Close all".
 */
internal data class RibbonStart(val card: Int, val onCloseAll: Boolean) {
    companion object {
        fun of(initial: Int, cards: Int, toCloseAll: Boolean, hasCloseAll: Boolean) =
            RibbonStart(initial.coerceIn(0, (cards - 1).coerceAtLeast(0)), toCloseAll && hasCloseAll)
    }
}

/**
 * A click (a mouse, the phone touchpad) on a card, its picture or its name, [open]s its app as OK
 * does; held, it closes the app as holding OK does ([closeOne], where apps can be closed); on
 * "Close all" it closes them ([closeAll]).
 */
internal fun AppSwitcherViews.Ribbon.clicks(
    open: (App) -> Unit,
    closeOne: ((Int, App) -> Unit)?,
    closeAll: () -> Unit,
) {
    cards.forEachIndexed { index, card ->
        val cell = card.frame.parent as? View ?: card.frame
        cell.setOnClickListener { open(card.app) }
        closeOne?.let { close ->
            cell.setOnLongClickListener {
                close(index, card.app)
                true
            }
        }
    }
    this.closeAll?.frame?.setOnClickListener { closeAll() }
}
