package dev.lumbina.tv.platform.accessibility

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import java.util.concurrent.Executors

/**
 * Recent apps over whatever is on screen, like Alt+Tab: ◀ ▶ pick, OK switches, Back closes;
 * holding OK closes the app instead when [show] allows it ([onClose]). It also closes by itself
 * when left alone. It never takes focus — the service feeds it the remote's keys ([onKey]) — so
 * the app underneath keeps running undisturbed.
 */
internal class AppSwitcherOverlay(
    private val context: Context,
    private val strings: Strings,
    private val onPick: (App) -> Unit,
    private val onClose: (App) -> Unit,
) {
    /** [closeHint] replaces [hint] where holding OK closes apps. */
    class Strings(val title: String, val hint: String, val closeHint: String, val empty: String)

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    // Banners come from the package manager, off the main thread (it also filters the remote's keys).
    private val loader = Executors.newSingleThreadExecutor { Thread(it, "switcher-artwork").apply { isDaemon = true } }
    private val artworks = LruCache<AppId, Artwork>(ARTWORK_CACHE_SIZE)
    private val hideRunnable = Runnable { hide() }
    private var root: View? = null
    private var scroller: HorizontalScrollView? = null
    private var cards: List<View> = emptyList()
    private var apps: List<App> = emptyList()
    private var selected = 0
    private var canClose = false

    /** OK went down while showing; a release without it (held from before) is left alone. */
    private var okDown = false

    /** OK was held past a long press: its release must not switch too. */
    private var held = false

    // Timed, not by auto-repeat: some remotes send no repeats while OK is held.
    private val longPress = Runnable {
        held = true
        apps.getOrNull(selected)?.let(onClose)
    }

    val isShowing: Boolean get() = root != null

    /** The app under the selection. */
    val selectedIndex: Int get() = selected

    fun show(apps: List<App>, initial: Int, canClose: Boolean = false) {
        hide()
        this.apps = apps
        this.canClose = canClose
        selected = initial.coerceIn(0, (apps.size - 1).coerceAtLeast(0))
        val params = overlayParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
        root = createView().takeIf { windowManager.addOverlay(it, params) } ?: return
        select(selected)
        keepOpen()
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(longPress)
        okDown = false
        held = false
        root?.let(windowManager::removeView)
        root = null
        scroller = null
        cards = emptyList()
    }

    /** Hides and stops loading artwork; the overlay is not used again. */
    fun dispose() {
        hide()
        loader.shutdownNow()
    }

    /**
     * Handles a key while showing; returns true if consumed. Pressing any other key closes the
     * switcher and is left to the caller (e.g. Home goes on to open Lumbina); releases and
     * auto-repeats of keys held from before are left alone.
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
                if (canClose) handler.postDelayed(longPress, LONG_PRESS_MILLIS)
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
        if (cards.isEmpty()) return
        selected = index.coerceIn(0, cards.lastIndex)
        cards.forEachIndexed { i, card ->
            val on = i == selected
            card.alpha = if (on) 1f else UNSELECTED_ALPHA
            // A foreground, so the banner doesn't cover the border.
            (card as FrameLayout).foreground = if (on) selectionBorder() else null
        }
        val card = cards[selected]
        val view = scroller ?: return
        view.post {
            // The overlay may have closed in the meantime, taking the card off screen.
            val row = card.parent as? View ?: return@post
            val target = card.left + row.left - (view.width - card.width) / 2
            view.smoothScrollTo(target.coerceAtLeast(0), 0)
        }
    }

    private fun createView(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(PANEL_PADDING_DP)
            setPadding(pad, pad, pad, pad)
            background = context.rounded(OverlayPalette.PANEL, PANEL_CORNER_DP)
        }
        panel.addView(context.label(strings.title, TITLE_SP))
        if (apps.isEmpty()) {
            panel.addView(context.label(strings.empty, HINT_SP).apply { setPadding(0, context.dp(GAP_DP), 0, 0) })
            return panel
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        cards = apps.map { app ->
            val card = card(app)
            row.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val gap = context.dp(GAP_DP)
                    setPadding(gap, gap, gap, 0)
                    addView(card, LinearLayout.LayoutParams(context.dp(CARD_WIDTH_DP), context.dp(CARD_HEIGHT_DP)))
                    addView(
                        context.label(app.label, LABEL_SP).apply {
                            maxWidth = context.dp(CARD_WIDTH_DP)
                            setSingleLine()
                            ellipsize = TextUtils.TruncateAt.END
                            setPadding(0, context.dp(LABEL_GAP_DP), 0, 0)
                        },
                    )
                },
            )
            card
        }
        scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            addView(row)
        }
        panel.addView(
            scroller,
            LinearLayout.LayoutParams(context.dp(ROW_WIDTH_DP), WRAP_CONTENT),
        )
        val hint = if (canClose) strings.closeHint else strings.hint
        panel.addView(context.label(hint, HINT_SP).apply { setPadding(0, context.dp(GAP_DP), 0, 0) })
        return panel
    }

    /** The app's TV banner filling the card, or its icon centered; the card's rounded corners clip it. */
    private fun card(app: App): View {
        val card = FrameLayout(context).apply {
            background = context.rounded(OverlayPalette.CARD, CARD_CORNER_DP)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
        val view = ImageView(context)
        card.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        val cached = artworks.get(app.id)
        if (cached != null) {
            show(view, cached)
        } else {
            loader.execute {
                val artwork = context.artwork(app)
                handler.post {
                    artworks.put(app.id, artwork)
                    show(view, artwork)
                }
            }
        }
        return card
    }

    private fun show(view: ImageView, artwork: Artwork) {
        view.setImageDrawable(artwork.image)
        view.scaleType = if (artwork.isBanner) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        val pad = if (artwork.isBanner) 0 else context.dp(ICON_PADDING_DP)
        (view.layoutParams as FrameLayout.LayoutParams).setMargins(pad, pad, pad, pad)
        view.requestLayout()
    }

    private fun selectionBorder() = GradientDrawable().apply {
        setStroke(context.dp(BORDER_DP), OverlayPalette.SELECTED_BORDER)
        cornerRadius = context.dp(CARD_CORNER_DP).toFloat()
    }

    private companion object {
        /** How long OK is held before it closes the selected app. */
        const val LONG_PRESS_MILLIS = 600L
        const val AUTO_HIDE_MILLIS = 10_000L
        const val ARTWORK_CACHE_SIZE = 32
        const val UNSELECTED_ALPHA = 0.55f
        const val BORDER_DP = 4
        const val PANEL_PADDING_DP = 24
        const val PANEL_CORNER_DP = 20
        const val CARD_CORNER_DP = 10
        const val CARD_WIDTH_DP = 176
        const val CARD_HEIGHT_DP = 99
        const val ROW_WIDTH_DP = 880
        const val GAP_DP = 12
        const val LABEL_GAP_DP = 6
        const val ICON_PADDING_DP = 20
        const val TITLE_SP = 22f
        const val LABEL_SP = 15f
        const val HINT_SP = 14f
    }
}

/** An app's TV banner, or its icon when [isBanner] is false. */
private class Artwork(val image: Drawable?, val isBanner: Boolean)

private fun Context.artwork(app: App): Artwork {
    val pm = packageManager
    val component = ComponentName(app.id.packageName, app.id.activityName)
    val banner = runCatching {
        pm.getActivityBanner(component) ?: pm.getApplicationBanner(app.id.packageName)
    }.getOrNull()
    if (banner != null) return Artwork(banner, isBanner = true)
    return Artwork(runCatching { pm.getActivityIcon(component) }.getOrNull(), isBanner = false)
}
