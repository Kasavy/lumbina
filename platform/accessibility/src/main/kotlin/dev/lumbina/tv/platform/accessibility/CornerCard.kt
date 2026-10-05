package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A card in the bottom-right corner over whatever is on screen: a message, buttons (the first
 * one chosen) and, for the warning before the device turns off, a bar running down. Like the
 * other overlays it never takes focus: its owner feeds it the remote's keys. Main thread.
 */
internal class CornerCard(private val context: Context) : PromptCard {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var text: TextView? = null
    private var bar: View? = null
    private var buttonViews: List<TextView> = emptyList()

    override var selected = 0
        private set

    /**
     * Shows or updates the card: [message], [buttons], and the bar at [fraction] (1 = full). With no
     * [fraction] there is no bar, and the message may take a few lines. False when the system refused the window.
     */
    override fun show(message: String, buttons: List<String>, fraction: Float?): Boolean {
        val stale = root == null || buttonViews.map { it.text } != buttons || (bar != null) != (fraction != null)
        if (stale) create(buttons, withBar = fraction != null)
        text?.text = message
        if (fraction == null) return root != null
        bar?.let { view ->
            (view.layoutParams as FrameLayout.LayoutParams).width =
                (context.dp(CARD_INNER_DP) * fraction.coerceIn(0f, 1f)).toInt()
            view.requestLayout()
        }
        return root != null
    }

    /** ◀ ▶ move between the buttons. */
    override fun move(step: Int) {
        select(selected + step)
    }

    override fun hide() {
        root?.let(windowManager::removeView)
        root = null
        text = null
        bar = null
        buttonViews = emptyList()
    }

    private fun create(buttons: List<String>, withBar: Boolean) {
        hide()
        selected = 0
        val gap = context.dp(GAP_DP)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(PADDING_DP)
            setPadding(pad, pad, pad, pad)
            background = context.rounded(OverlayPalette.PANEL, CORNER_DP)
        }
        val message = context.label("", MESSAGE_SP).apply { maxLines = if (withBar) 1 else MESSAGE_LINES }
        card.addView(message, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = gap })
        val fill = if (withBar) bar(card, gap) else null
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buttonViews = buttons.map { label ->
            context.label(label, BUTTON_SP).apply {
                val h = context.dp(BUTTON_H_DP)
                val v = context.dp(BUTTON_V_DP)
                setPadding(h, v, h, v)
            }.also { view ->
                row.addView(
                    view,
                    LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = gap },
                )
            }
        }
        // The message wraps to the card's width, and the card is as wide as its buttons need.
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        if (!withBar) message.maxWidth = maxOf(context.dp(CARD_INNER_DP), row.measuredWidth)
        card.addView(row, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        val params = overlayParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            x = context.dp(MARGIN_DP)
            y = context.dp(MARGIN_DP)
        }
        if (!windowManager.addOverlay(card, params)) return
        root = card
        text = message
        bar = fill
        select(0)
    }

    /** The bar under the message: a track with the part still left filled. */
    private fun bar(card: LinearLayout, gap: Int): View {
        val track = FrameLayout(context).apply { background = context.rounded(OverlayPalette.TRACK, BAR_CORNER_DP) }
        val fill = View(context).apply { background = context.rounded(OverlayPalette.ACCENT, BAR_CORNER_DP) }
        track.addView(fill, FrameLayout.LayoutParams(context.dp(CARD_INNER_DP), context.dp(BAR_DP)))
        card.addView(
            track,
            LinearLayout.LayoutParams(context.dp(CARD_INNER_DP), context.dp(BAR_DP)).apply { bottomMargin = gap },
        )
        return fill
    }

    private fun select(index: Int) {
        if (buttonViews.isEmpty()) return
        selected = index.coerceIn(0, buttonViews.lastIndex)
        buttonViews.forEachIndexed { i, view ->
            val on = i == selected
            view.background =
                context.rounded(if (on) OverlayPalette.SELECTED else OverlayPalette.CARD, BUTTON_CORNER_DP)
            view.setTextColor(if (on) OverlayPalette.SELECTED_TEXT else OverlayPalette.TEXT)
        }
    }

    private companion object {
        const val PADDING_DP = 16
        const val GAP_DP = 11
        const val CORNER_DP = 12
        const val MARGIN_DP = 36
        const val CARD_INNER_DP = 270
        const val BAR_DP = 3
        const val BAR_CORNER_DP = 2
        const val BUTTON_H_DP = 19
        const val BUTTON_V_DP = 8
        const val BUTTON_CORNER_DP = 20
        const val MESSAGE_SP = 15f
        const val MESSAGE_LINES = 4
        const val BUTTON_SP = 12f
    }
}
