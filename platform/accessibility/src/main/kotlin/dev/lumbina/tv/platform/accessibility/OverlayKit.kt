package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Colors of the service's overlays (plain Views, independent of the launcher's Compose theme). */
internal object OverlayPalette {
    const val TEXT = 0xFFE8E9EC.toInt()
    const val PANEL = 0xF215181E.toInt()
    const val CARD = 0xFF2A2E36.toInt()
    const val SELECTED = 0xFFE8E9EC.toInt()
    const val SELECTED_TEXT = 0xFF15181E.toInt()
    const val SELECTED_BORDER = 0xFFFFFFFF.toInt()
    const val CAPTION_TEXT = 0xFFFFFFFF.toInt()
    const val CAPTION = 0xE0202830.toInt()
    const val DIVIDER = 0x1FFFFFFF
    const val ACCENT = 0xFFFFB547.toInt()
    const val TRACK = 0x1FFFFFFF
}

/** OK on the remote, or Enter. */
internal val KeyEvent.isOk: Boolean
    get() = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER

/** Back on the remote, or Escape. */
internal val KeyEvent.isBack: Boolean
    get() = keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE

private val DEBUG_KEYS = mapOf(
    "up" to KeyEvent.KEYCODE_DPAD_UP,
    "down" to KeyEvent.KEYCODE_DPAD_DOWN,
    "left" to KeyEvent.KEYCODE_DPAD_LEFT,
    "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
    "ok" to KeyEvent.KEYCODE_DPAD_CENTER,
    "hold" to KeyEvent.KEYCODE_DPAD_CENTER,
    "back" to KeyEvent.KEYCODE_BACK,
)

/**
 * Debug builds: presses the key [command] names (up, down, left, right, ok, back; "hold" keeps OK
 * down past a long press) on an overlay's [onKey], since injected keys don't reach the service.
 * False for any other command.
 */
internal fun debugPress(command: String, onKey: (KeyEvent) -> Boolean): Boolean {
    val code = DEBUG_KEYS[command] ?: return false
    val time = SystemClock.uptimeMillis()
    onKey(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0))
    val up = { onKey(KeyEvent(time, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0)) }
    // A hold is released later, as a finger would: long presses are timed, not counted in repeats.
    if (command == "hold") Handler(Looper.getMainLooper()).postDelayed({ up() }, DEBUG_HOLD_MILLIS) else up()
    return true
}

private const val DEBUG_HOLD_MILLIS = 900L

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.rounded(color: Int, cornerDp: Int) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = cornerDp * resources.displayMetrics.density
}

internal fun Context.label(text: String, sp: Float, color: Int = OverlayPalette.TEXT) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    setTextColor(color)
}

/**
 * Adds an overlay window, or returns false when the service has no window token yet (or any
 * more): right after (re)connecting the system can refuse it, and that must not crash the service.
 */
internal fun WindowManager.addOverlay(view: View, params: WindowManager.LayoutParams): Boolean = try {
    addView(view, params)
    true
} catch (_: WindowManager.BadTokenException) {
    false
}

/**
 * An accessibility overlay window: needs no extra permission, and never takes focus or touches,
 * so the app underneath keeps running (the service feeds the overlay the remote's keys).
 */
internal fun overlayParams(width: Int, height: Int, gravity: Int, extraFlags: Int = 0) = WindowManager.LayoutParams(
    width,
    height,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or extraFlags,
    PixelFormat.TRANSLUCENT,
).also { it.gravity = gravity }
