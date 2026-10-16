package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A plate at the bottom center, over whatever is on screen: at the left a sign of what is going on
 * ([Mark]), at the right the text. Shown through an accessibility overlay window, so it needs no
 * extra permission and never takes focus or touches. A notice may carry something OK does
 * ([Action]): while it shows, OK is the plate's ([onKey]) and any other button takes it away.
 * A question ([choose]) has its answers as buttons under it: ◀ ▶ choose, OK takes one.
 * Toasts are not an option: some firmware doesn't show a service's toasts.
 */
internal class VoiceOverlay(private val context: Context) {

    /** What the plate is saying, by its sign. */
    enum class Mark(val icon: Int?) {
        /** A line with no sign: not about speech. */
        None(null),
        Listening(R.drawable.ic_voice_mic),
        Working(R.drawable.ic_voice_working),
        Done(R.drawable.ic_voice_done),
        Failed(R.drawable.ic_voice_mic_off),
        Question(R.drawable.ic_voice_question),
    }

    /** What OK does while a notice shows, and the line that says so. */
    class Action(val hint: String, val run: () -> Unit)

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var root: View? = null
    private var sign: ImageView? = null
    private var label: TextView? = null
    private var note: TextView? = null
    private var options: LinearLayout? = null
    private var action: Action? = null
    private var choice: Choice? = null

    /** The key whose press the plate took: its repeats and its release are the plate's too. */
    private var taken: Int? = null

    /** The answers on screen: which one is chosen, and what taking one does. */
    private class Choice(val count: Int, val millis: Long, val onPick: (Int) -> Unit) {
        var selected = 0
    }

    fun show(text: String, mark: Mark = Mark.None) = put(text, mark, null)

    /** Shows [text], then hides after [millis]; with an [action], OK runs it meanwhile. */
    fun flash(text: String, millis: Long, mark: Mark = Mark.None, action: Action? = null) {
        put(text, mark, action)
        handler.postDelayed(hideRunnable, millis)
    }

    /**
     * Asks [question] with [answers] as buttons, the first chosen; gone after [millis] with no
     * button pressed. [onPick] gets the place of the answer OK was pressed on.
     */
    fun choose(question: String, answers: List<String>, millis: Long, onPick: (Int) -> Unit) {
        put(question, Mark.Question, null)
        val row = options ?: return
        val made = Choice(answers.size, millis, onPick)
        choice = made
        answers.forEach { answer ->
            val pad = context.dp(OPTION_PAD_DP)
            val button = context.label(answer, OPTION_SP).apply {
                setPadding(pad * 2, pad, pad * 2, pad)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = context.dp(OPTION_WIDTH_DP)
            }
            row.addView(button, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = pad })
        }
        row.visibility = View.VISIBLE
        mark(made)
        fit()
        handler.postDelayed(hideRunnable, millis)
    }

    /** Paints the chosen answer apart from the others. */
    private fun mark(choice: Choice) {
        val row = options ?: return
        for (index in 0 until row.childCount) {
            val button = row.getChildAt(index) as TextView
            val chosen = index == choice.selected
            val ground = if (chosen) OverlayPalette.accent else OverlayPalette.track
            button.background = context.rounded(ground, OPTION_CORNER_DP)
            button.setTextColor(if (chosen) OverlayPalette.onAccent else OverlayPalette.text)
        }
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        root?.let(windowManager::removeView)
        root = null
        sign = null
        label = null
        note = null
        options = null
        action = null
        choice = null
    }

    /**
     * True for the keys the plate takes: OK on a notice with an action; ◀ ▶, OK and Back on a
     * question. Any other button takes the plate away and stays the app's.
     */
    fun onKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return (event.keyCode == taken).also { if (it) taken = null }
        if (event.repeatCount > 0) return event.keyCode == taken
        val took = choice?.let { onChoiceKey(it, event) } ?: onNoticeKey(event)
        if (took) taken = event.keyCode
        return took
    }

    private fun onNoticeKey(event: KeyEvent): Boolean {
        val run = action?.run ?: return false
        hide()
        if (event.isOk) run()
        return event.isOk
    }

    private fun onChoiceKey(choice: Choice, event: KeyEvent): Boolean {
        val step = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> 0
        }
        if (step != 0) {
            choice.selected = (choice.selected + step).coerceIn(0, choice.count - 1)
            mark(choice)
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, choice.millis)
            return true
        }
        hide()
        if (event.isOk) choice.onPick(choice.selected)
        return event.isOk || event.isBack
    }

    private fun put(text: String, mark: Mark, action: Action?) {
        handler.removeCallbacks(hideRunnable)
        if (root == null) create()
        this.action = action
        choice = null
        options?.apply {
            removeAllViews()
            visibility = View.GONE
        }
        label?.text = text
        sign?.apply {
            visibility = if (mark.icon == null) View.GONE else View.VISIBLE
            mark.icon?.let(::setImageResource)
            val color = if (mark == Mark.Failed) OverlayPalette.muted else OverlayPalette.accent
            imageTintList = ColorStateList.valueOf(color)
        }
        note?.apply {
            this.text = action?.hint
            visibility = if (action == null) View.GONE else View.VISIBLE
        }
        fit()
    }

    /**
     * The window is as wide as its words: a shorter line must not stay in the longer one's plate.
     * The width is worked out here: left to the system, a window that wraps its content is first
     * tried at a dialog's width, and a row of answers is squeezed into it.
     */
    private fun fit() {
        val plate = root ?: return
        val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        plate.measure(free, free)
        val params = plate.layoutParams as WindowManager.LayoutParams
        val most = context.resources.displayMetrics.widthPixels - 2 * context.dp(BOTTOM_MARGIN_DP)
        params.width = minOf(plate.measuredWidth, most)
        windowManager.updateViewLayout(plate, params)
    }

    private fun create() {
        val pad = context.dp(PADDING_DP)
        val icon = ImageView(context)
        val text = context.label("", TEXT_SP, OverlayPalette.text).apply {
            maxWidth = context.dp(TEXT_WIDTH_DP)
        }
        val hint = context.label("", OverlayDimens.HINT_SP, OverlayPalette.muted).apply {
            setPadding(0, context.dp(HINT_GAP_DP), 0, 0)
        }
        val answers = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(OPTIONS_GAP_DP), 0, 0)
            visibility = View.GONE
        }
        val words = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text)
            addView(hint)
            addView(answers)
        }
        val plate = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad * 2, pad, pad * 2, pad)
            background = context.rounded(OverlayPalette.surface, CORNER_DP)
            val side = context.dp(ICON_DP)
            addView(icon, LinearLayout.LayoutParams(side, side).apply { marginEnd = pad })
            addView(words)
        }
        val params = overlayParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { y = context.dp(BOTTOM_MARGIN_DP) }
        if (!windowManager.addOverlay(plate, params)) return
        root = plate
        sign = icon
        label = text
        note = hint
        options = answers
    }

    private companion object {
        const val TEXT_SP = 20f
        const val HINT_GAP_DP = 4
        const val OPTION_SP = 18f
        const val OPTION_PAD_DP = 8
        const val OPTION_WIDTH_DP = 190
        const val OPTION_CORNER_DP = 18
        const val OPTIONS_GAP_DP = 10
        const val TEXT_WIDTH_DP = 620
        const val ICON_DP = 26
        const val PADDING_DP = 14
        const val CORNER_DP = 26
        const val BOTTOM_MARGIN_DP = 48
    }
}
