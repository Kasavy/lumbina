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
 * OK closes the app instead where [show] says it may ([onClose]). It also closes by itself when
 * left alone. It never takes focus — the service feeds it the remote's keys ([onKey]) — so the
 * app underneath keeps running undisturbed.
 */
internal class AppSwitcherOverlay(
    context: Context,
    texts: AppSwitcherViews.Texts,
    private val onPick: (App) -> Unit,
    onClose: (App) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val views = AppSwitcherViews(context, texts)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var root: View? = null
    private var ribbon: AppSwitcherViews.Ribbon? = null
    private var apps: List<App> = emptyList()
    private var selected = 0
    private var close = CloseApps.No

    /** The apps Lumbina is closing now: their cards say so until the device answers. */
    private val closing = mutableSetOf<AppId>()

    /** OK went down while showing; a release without it (held from before) does nothing. */
    private var okDown = false

    /** OK was held past a long press: its release must not switch too. */
    private var held = false

    // Timed, not by auto-repeat: some remotes send no repeats while OK is held.
    private val longPress = Runnable {
        held = true
        apps.getOrNull(selected)?.takeUnless { it.id in closing }?.let { app ->
            closing += app.id
            select(selected)
            onClose(app)
        }
    }

    val isShowing: Boolean get() = root != null

    /** The app under the selection. */
    val selectedIndex: Int get() = selected

    /**
     * Shows the ribbon. [keepKeys]: it is shown anew over itself (what it says about closing
     * changed), and an OK being held goes on being held.
     */
    fun show(apps: List<App>, initial: Int, close: CloseApps = CloseApps.No, keepKeys: Boolean = false) {
        if (keepKeys) removeWindow() else hide()
        this.apps = apps
        this.close = close
        closing.retainAll(apps.map { it.id }.toSet())
        selected = initial.coerceIn(0, (apps.size - 1).coerceAtLeast(0))
        val made = views.ribbon(apps, close)
        val params = overlayParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM)
        root = made.view.takeIf { windowManager.addOverlay(it, params) } ?: return
        ribbon = made
        select(selected)
        keepOpen()
    }

    /** [app] was closed, or could not be: either way its card no longer says "closing". */
    fun closed(app: App) {
        closing -= app.id
        if (isShowing) select(selected)
    }

    fun hide() {
        handler.removeCallbacks(longPress)
        okDown = false
        held = false
        removeWindow()
    }

    private fun removeWindow() {
        handler.removeCallbacks(hideRunnable)
        root?.let(windowManager::removeView)
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
     * auto-repeats of the switcher's own keys held from before are taken and do nothing.
     */
    fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when {
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> if (down) select(selected - 1)

            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) select(selected + 1)

            event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> Unit

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

            else -> {
                handler.removeCallbacks(longPress)
                val app = apps.getOrNull(selected)
                hide()
                app?.let(onPick)
            }
        }
    }

    private fun keepOpen() {
        handler.removeCallbacks(hideRunnable)
        if (isShowing) handler.postDelayed(hideRunnable, AUTO_HIDE_MILLIS)
    }

    private fun select(index: Int) {
        val shown = ribbon ?: return
        if (shown.cards.isEmpty()) return
        selected = index.coerceIn(0, shown.cards.lastIndex)
        shown.cards.forEachIndexed { i, card -> views.mark(card, on = i == selected, closing = card.app.id in closing) }
        val card = shown.cards[selected].frame
        val scroller = shown.scroller ?: return
        scroller.post {
            // The overlay may have closed in the meantime, taking the card off screen.
            val cell = card.parent as? View ?: return@post
            val target = cell.left - (scroller.width - cell.width) / 2
            scroller.smoothScrollTo(target.coerceAtLeast(0), 0)
        }
    }

    private companion object {
        const val AUTO_HIDE_MILLIS = 10_000L
    }
}
