package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A card in the bottom-right corner over whatever is on screen: a message, a bar running down
 * where time is short, buttons (the first one chosen) and a line about the keys, each set apart
 * from the next: the card is read from across the room, in seconds. Like the other overlays it
 * never takes focus: its owner feeds it the remote's keys. A card made with another one [under] it
 * stands over that one while both show. Main thread.
 */
internal class CornerCard(private val context: Context, private val under: CornerCard? = null) : PromptCard {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var root: View? = null
    private var text: TextView? = null
    private var bar: View? = null
    private var note: TextView? = null
    private var buttonViews: List<TextView> = emptyList()

    /** The card standing over this one: it moves as this one comes and goes. */
    private var over: CornerCard? = null

    init {
        under?.over = this
    }

    override var selected = 0
        private set

    /**
     * Shows or updates the card: [message], [buttons], the bar at [fraction] (1 = full) and the
     * [hint] under the buttons. With no [fraction] there is no bar, and the message may take a few
     * lines. False when the system refused the window.
     */
    override fun show(message: String, buttons: List<String>, fraction: Float?, hint: String?): Boolean {
        handler.removeCallbacks(hideRunnable)
        val made = Made(buttons, withBar = fraction != null, withHint = hint != null)
        if (root == null || made != shown) create(made)
        text?.text = message
        note?.text = hint
        if (fraction != null) {
            bar?.let { view ->
                (view.layoutParams as FrameLayout.LayoutParams).width =
                    (context.dp(CARD_INNER_DP) * fraction.coerceIn(0f, 1f)).toInt()
                view.requestLayout()
            }
        }
        return root != null
    }

    /** [message] alone for [millis]: what was just done, or what could not be. */
    fun flash(message: String, millis: Long) {
        if (show(message, emptyList(), fraction = null, hint = null)) handler.postDelayed(hideRunnable, millis)
    }

    /** ◀ ▶ move between the buttons. */
    override fun move(step: Int) {
        select(selected + step)
    }

    override fun hide() {
        handler.removeCallbacks(hideRunnable)
        root?.let(windowManager::removeView)
        root = null
        text = null
        bar = null
        note = null
        buttonViews = emptyList()
        over?.place()
    }

    /** What a card is built of: it is built anew when any of this changes. */
    private data class Made(val buttons: List<String>, val withBar: Boolean, val withHint: Boolean)

    private var shown: Made? = null

    private fun create(made: Made) {
        hide()
        selected = 0
        shown = made
        val gap = context.dp(GAP_DP)
        val below = { LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = gap } }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(PADDING_DP)
            setPadding(pad, pad, pad, pad)
            background = context.rounded(OverlayPalette.surface, CORNER_DP)
        }
        val message = context.label("", MESSAGE_SP).apply { maxLines = if (made.withBar) 1 else MESSAGE_LINES }
        card.addView(message)
        val fill = if (made.withBar) bar(card, gap) else null
        val row = buttons(made.buttons, gap)
        // The message wraps to the card's width, and the card is as wide as its buttons need.
        val width = maxOf(context.dp(CARD_INNER_DP), row.measuredWidth)
        if (!made.withBar) message.maxWidth = width
        if (made.buttons.isNotEmpty()) card.addView(row, below())
        val hint = context.label("", OverlayDimens.HINT_SP, OverlayPalette.muted).takeIf { made.withHint }
        hint?.maxWidth = width
        hint?.let { card.addView(it, below()) }
        if (!windowManager.addOverlay(card, params(card))) return
        root = card
        text = message
        bar = fill
        note = hint
        select(0)
        over?.place()
    }

    /** The row of buttons, measured, with [buttonViews] set to them. */
    private fun buttons(labels: List<String>, gap: Int): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buttonViews = labels.map { label ->
            context.label(label, BUTTON_SP).apply {
                val h = context.dp(BUTTON_H_DP)
                val v = context.dp(BUTTON_V_DP)
                setPadding(h, v, h, v)
            }.also { view ->
                row.addView(view, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = gap })
            }
        }
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        return row
    }

    /** In the corner, or over the card [under] this one while that one shows. */
    private fun params(card: View) = overlayParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
        x = context.dp(MARGIN_DP)
        y = context.dp(MARGIN_DP) + (under?.height()?.takeIf { it > 0 }?.plus(context.dp(STACK_GAP_DP)) ?: 0)
        // Measured here so a card that comes first knows its own height before it is laid out.
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    }

    private fun height(): Int = root?.let { if (it.height > 0) it.height else it.measuredHeight } ?: 0

    /** The card under this one came or went. */
    private fun place() {
        root?.let { windowManager.updateViewLayout(it, params(it)) }
    }

    /** The bar under the message: a track with the part still left filled. */
    private fun bar(card: LinearLayout, gap: Int): View {
        val track = FrameLayout(context).apply { background = context.rounded(OverlayPalette.track, BAR_CORNER_DP) }
        val fill = View(context).apply { background = context.rounded(OverlayPalette.accent, BAR_CORNER_DP) }
        track.addView(fill, FrameLayout.LayoutParams(context.dp(CARD_INNER_DP), context.dp(BAR_DP)))
        card.addView(
            track,
            LinearLayout.LayoutParams(context.dp(CARD_INNER_DP), context.dp(BAR_DP)).apply { topMargin = gap },
        )
        return fill
    }

    private fun select(index: Int) {
        if (buttonViews.isEmpty()) return
        selected = index.coerceIn(0, buttonViews.lastIndex)
        buttonViews.forEachIndexed { i, view ->
            val on = i == selected
            view.background =
                context.rounded(if (on) OverlayPalette.selected else OverlayPalette.card, BUTTON_CORNER_DP)
            view.setTextColor(if (on) OverlayPalette.selectedText else OverlayPalette.text)
        }
    }

    private companion object {
        const val PADDING_DP = 20
        const val GAP_DP = 14
        const val CORNER_DP = 18
        const val MARGIN_DP = 36
        const val STACK_GAP_DP = 12
        const val CARD_INNER_DP = 300
        const val BAR_DP = 4
        const val BAR_CORNER_DP = 2
        const val BUTTON_H_DP = 20
        const val BUTTON_V_DP = 9
        const val BUTTON_CORNER_DP = 20
        const val MESSAGE_SP = 16f
        const val MESSAGE_LINES = 4
        const val BUTTON_SP = 13f
    }
}
