package dev.lumbina.tv.platform.accessibility

import android.content.ComponentName
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import java.util.concurrent.Executors

/** Whether holding OK in the recent apps closes one, and "Close all" beside the title closes them all. */
internal enum class CloseApps {
    /** This device or edition never does. */
    No,
    Yes,

    /** It would, but Lumbina's connection to debugging is not there now. */
    NoAccess,
}

/**
 * The views of [AppSwitcherOverlay]: a ribbon of cards along the bottom of the screen, over a
 * shade that leaves the app above it in sight. Only the selected card carries its app's name.
 * Where apps may be closed and there are two or more, "Close all" stands beside the title.
 */
internal class AppSwitcherViews(private val context: Context, private val textsOf: () -> Texts) {

    /** Read anew as each ribbon is made: Lumbina's own language may have changed since the service started. */
    private var texts: Texts = textsOf()

    /**
     * What the ribbon says; [closeKeys] replaces [keys] where holding OK closes apps, and
     * [closeAllKeys] while "Close all" is selected. [closeAllCount] is "Close all" with how many it closes.
     */
    class Texts(
        val title: String,
        val keys: String,
        val closeKeys: String,
        val needAccess: String,
        val empty: String,
        val emptyKeys: String,
        val closing: String,
        val closeAllKeys: String,
        val closeAllCount: (Int) -> String,
    )

    /** One app's card and the line under it. */
    class Card(val app: App, val frame: FrameLayout, val caption: TextView)

    /** "Close all" beside the title: a button as the service's cards have, a cross and how many. */
    class Tile(val frame: LinearLayout, val icon: ImageView, val label: TextView)

    /**
     * The whole ribbon; [scroller] is null when there are no apps, [closeAll] when the ribbon has
     * no "Close all". [keys] is the hint line, saying [keysText] unless "Close all" is selected.
     */
    class Ribbon(
        val view: View,
        val scroller: HorizontalScrollView?,
        val cards: List<Card>,
        val closeAll: Tile? = null,
        val keys: TextView? = null,
        val keysText: String = "",
    )

    /** The first card's place from the side: the ribbon scrolls only to keep the selected one this far in. */
    val edge: Int = context.dp(SIDE_DP) - context.dp(OverlayDimens.GAP_DP) / 2

    private val handler = Handler(Looper.getMainLooper())

    // Banners come from the package manager, off the main thread (it also filters the remote's keys).
    private val loader = Executors.newSingleThreadExecutor { Thread(it, "switcher-artwork").apply { isDaemon = true } }
    private val artworks = LruCache<AppId, Artwork>(ARTWORK_CACHE_SIZE)

    /** Stops loading artwork; the views are not built again. */
    fun dispose() {
        loader.shutdownNow()
    }

    fun ribbon(apps: List<App>, close: CloseApps): Ribbon {
        texts = textsOf()
        val side = context.dp(SIDE_DP)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(SHADE_DP), 0, context.dp(BOTTOM_DP))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                // Dark enough for the title a quarter of the way down, whatever is under it.
                intArrayOf(0, OverlayPalette.shade, OverlayPalette.shade, OverlayPalette.shade, OverlayPalette.shade),
            )
        }
        val line = { text: String, sp: Float, color: Int ->
            context.label(text, sp, color).apply { setPadding(side, 0, side, 0) }
        }
        val closeAll = if (close == CloseApps.Yes && apps.size >= CLOSE_ALL_FROM) closeAllButton(apps.size) else null
        column.addView(header(closeAll))
        if (apps.isEmpty()) {
            column.addView(
                line(texts.empty, LABEL_SP, OverlayPalette.text).apply {
                    setPadding(
                        side,
                        side / 2,
                        side,
                        side / 2,
                    )
                },
            )
            column.addView(line(texts.emptyKeys, OverlayDimens.HINT_SP, OverlayPalette.muted))
            return Ribbon(column, null, emptyList())
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(side - context.dp(OverlayDimens.GAP_DP) / 2, 0, side, 0)
        }
        val cards = apps.map { app -> card(app).also { row.addView(it.frame.parent as View) } }
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(row)
        }
        column.addView(scroller, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val hint = { text: String -> line(text, OverlayDimens.HINT_SP, OverlayPalette.muted) }
        val keysText = if (close == CloseApps.Yes) texts.closeKeys else texts.keys
        val keys = hint(keysText)
        column.addView(keys)
        if (close == CloseApps.NoAccess) column.addView(hint(texts.needAccess))
        return Ribbon(column, scroller, cards, closeAll, keys, keysText)
    }

    /**
     * How a card looks: the selected one is [on], bright, bordered and named; one whose app is
     * [closing] is dim and says so whether selected or not.
     */
    fun mark(card: Card, on: Boolean, closing: Boolean) {
        card.frame.alpha = when {
            closing -> CLOSING_ALPHA
            on -> 1f
            else -> UNSELECTED_ALPHA
        }
        // A foreground, so the banner doesn't cover the border.
        card.frame.foreground = if (on) selectionBorder() else null
        card.caption.text = if (closing) texts.closing else card.app.label
        card.caption.visibility = if (on || closing) View.VISIBLE else View.INVISIBLE
    }

    /** How "Close all" looks, as a card's button does: [on] is selected; the hint follows it. */
    fun markCloseAll(ribbon: Ribbon, on: Boolean, count: Int) {
        val tile = ribbon.closeAll ?: return
        tile.label.text = texts.closeAllCount(count)
        tile.frame.background =
            context.rounded(if (on) OverlayPalette.selected else OverlayPalette.card, BUTTON_CORNER_DP)
        val color = if (on) OverlayPalette.selectedText else OverlayPalette.text
        tile.label.setTextColor(color)
        tile.icon.setColorFilter(color)
        ribbon.keys?.text = if (on) texts.closeAllKeys else ribbon.keysText
    }

    /** The title and "Close all" on one line, centered on each other; the button ends at the side margin. */
    private fun header(closeAll: Tile?) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val side = context.dp(SIDE_DP)
        setPadding(side, 0, side, 0)
        val title = context.label(texts.title, TITLE_SP, OverlayPalette.text).apply { typeface = MEDIUM }
        addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        closeAll?.let { addView(it.frame) }
    }

    /** A cross and "Close all · N" in a rounded button, as the service's cards have. */
    private fun closeAllButton(count: Int): Tile {
        val icon = ImageView(context).apply { setImageResource(R.drawable.ic_close) }
        val label = context.label(texts.closeAllCount(count), BUTTON_SP).apply { setSingleLine() }
        val frame = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val v = context.dp(BUTTON_V_DP)
            setPadding(context.dp(BUTTON_START_DP), v, context.dp(BUTTON_END_DP), v)
            addView(icon, LinearLayout.LayoutParams(context.dp(CLOSE_ICON_DP), context.dp(CLOSE_ICON_DP)))
            addView(
                label,
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = context.dp(ICON_GAP_DP) },
            )
        }
        return Tile(frame, icon, label)
    }

    /** A card's place in the row: the card, the line under it. */
    private fun cell(frame: FrameLayout, caption: TextView) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.START
        val gap = context.dp(OverlayDimens.GAP_DP) / 2
        setPadding(gap, context.dp(OverlayDimens.GAP_DP), gap, 0)
        addView(frame, LinearLayout.LayoutParams(context.dp(CARD_WIDTH_DP), context.dp(CARD_HEIGHT_DP)))
        addView(caption)
    }

    /** The app's TV banner filling the card, or its icon centered; the card's rounded corners clip it. */
    private fun card(app: App): Card {
        val frame = FrameLayout(context).apply {
            background = context.rounded(OverlayPalette.card, CARD_CORNER_DP)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
        val picture = ImageView(context)
        frame.addView(picture, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val caption = context.label(app.label, LABEL_SP).apply {
            maxWidth = context.dp(CARD_WIDTH_DP)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, context.dp(CAPTION_GAP_DP), 0, context.dp(OverlayDimens.GAP_DP))
        }
        cell(frame, caption)
        val cached = artworks.get(app.id)
        if (cached != null) {
            show(picture, cached)
        } else if (!loader.isShutdown) {
            // Disposed with the service: the card stays without its picture.
            loader.execute {
                val artwork = context.artwork(app)
                handler.post {
                    artworks.put(app.id, artwork)
                    show(picture, artwork)
                }
            }
        }
        return Card(app, frame, caption)
    }

    private fun show(view: ImageView, artwork: Artwork) {
        view.setImageDrawable(artwork.image)
        view.scaleType = if (artwork.isBanner) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        val pad = if (artwork.isBanner) 0 else context.dp(ICON_PADDING_DP)
        (view.layoutParams as FrameLayout.LayoutParams).setMargins(pad, pad, pad, pad)
        view.requestLayout()
    }

    /**
     * The stroke runs half its width in from the card's edge, so its corners are rounded that much
     * less: its outer edge then follows the card's own corner, with no sliver of the banner outside.
     */
    private fun selectionBorder() = GradientDrawable().apply {
        val border = context.dp(BORDER_DP)
        setStroke(border, OverlayPalette.accent)
        cornerRadius = context.dp(CARD_CORNER_DP) - border / 2f
    }

    private companion object {
        const val ARTWORK_CACHE_SIZE = 32
        const val UNSELECTED_ALPHA = 0.6f
        const val CLOSING_ALPHA = 0.3f
        const val BORDER_DP = 3

        // As home's rows: the same gap to the screen's side, a tile's size and corners.
        const val SIDE_DP = 48
        const val SHADE_DP = 96
        const val BOTTOM_DP = 28
        const val CARD_CORNER_DP = 10
        const val CARD_WIDTH_DP = 176
        const val CARD_HEIGHT_DP = 99
        const val CAPTION_GAP_DP = 6
        const val ICON_PADDING_DP = 20
        const val CLOSE_ICON_DP = 16
        const val ICON_GAP_DP = 6

        // As the service's cards' buttons (CornerCard), a little roomier on the side of the cross.
        const val BUTTON_START_DP = 16
        const val BUTTON_END_DP = 20
        const val BUTTON_V_DP = 9
        const val BUTTON_CORNER_DP = 20
        const val BUTTON_SP = 13f

        /** With one app, holding OK on it closes as much. */
        const val CLOSE_ALL_FROM = 2
        const val TITLE_SP = 16f
        const val LABEL_SP = 14f
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
