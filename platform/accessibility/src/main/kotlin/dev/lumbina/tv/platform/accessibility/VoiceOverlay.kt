package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.TextView

/**
 * Caption at the bottom of the screen showing what voice input hears. An accessibility
 * overlay needs no extra permission; plain Views keep it independent of Compose.
 * Toasts are not an option: some firmware doesn't show a service's toasts.
 */
internal class VoiceOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var view: TextView? = null

    fun show(text: String) {
        handler.removeCallbacks(hideRunnable)
        val label = view ?: createView()?.also { view = it }
        label?.text = text
    }

    /** Shows [text], then hides after [millis]. */
    fun flash(text: String, millis: Long) {
        show(text)
        handler.postDelayed(hideRunnable, millis)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        view?.let(windowManager::removeView)
        view = null
    }

    private fun createView(): TextView? {
        val label = context.label("", TEXT_SP, OverlayPalette.CAPTION_TEXT).apply {
            val pad = context.dp(PADDING_DP)
            setPadding(pad * 2, pad, pad * 2, pad)
            background = context.rounded(OverlayPalette.CAPTION, CORNER_DP)
        }
        val params = overlayParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { y = context.dp(BOTTOM_MARGIN_DP) }
        return label.takeIf { windowManager.addOverlay(it, params) }
    }

    private companion object {
        const val TEXT_SP = 22f
        const val PADDING_DP = 16
        const val CORNER_DP = 24
        const val BOTTOM_MARGIN_DP = 48
    }
}
