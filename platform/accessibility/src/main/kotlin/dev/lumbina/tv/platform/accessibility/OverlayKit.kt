package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.domain.theme.ThemeSpec
import dev.lumbina.tv.core.domain.theme.Themes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Colors of the service's overlays (plain Views, independent of the launcher's Compose theme):
 * the roles of the theme and the accent the user chose ([use]), so a panel looks as the
 * launcher's. An overlay takes them when it is made; one on screen keeps what it had.
 */
internal object OverlayPalette {
    @Volatile private var theme: ThemeSpec = Themes.Dark

    @Volatile private var chosenAccent: Long? = null

    /** The theme in use, and the accent the user chose (null: the theme's own). */
    fun use(theme: ThemeSpec, accent: Long?) {
        this.theme = theme
        chosenAccent = accent
    }

    val text: Int get() = role(ThemeRole.Text)

    /** The launcher's panel: one that looks as the launcher's own. */
    val surface: Int get() = role(ThemeRole.Panel)

    /** What covers the screen under a strip of Lumbina's own along its bottom: the theme's ground, nearly opaque. */
    val shade: Int get() = withAlpha(role(ThemeRole.Background), SHADE_ALPHA)

    /** Text that explains rather than names: a hint, a path, a note. */
    val muted: Int get() = role(ThemeRole.TextSecondary)
    val card: Int get() = role(ThemeRole.PanelRaised)

    /** The row or card with the focus: the text color as its ground, the panel's as its text. */
    val selected: Int get() = role(ThemeRole.Text)
    val selectedText: Int get() = role(ThemeRole.Panel)
    val divider: Int get() = withAlpha(role(ThemeRole.Text), LINE_ALPHA)
    val track: Int get() = withAlpha(role(ThemeRole.Text), LINE_ALPHA)
    val accent: Int get() = (chosenAccent ?: theme.color(ThemeRole.Accent)).toInt()

    /** Text on [accent]: a chosen accent is always light, so it takes the dark theme's. */
    val onAccent: Int get() = (if (chosenAccent == null) theme else Themes.Dark).color(ThemeRole.OnAccent).toInt()

    /** Over a picture (an app's artwork, a video): the same in every theme. */
    const val CAPTION_TEXT = 0xFFFFFFFF.toInt()
    const val CAPTION = 0xE0202830.toInt()

    private fun role(role: ThemeRole): Int = theme.color(role).toInt()

    private fun withAlpha(color: Int, alpha: Int): Int = (color and RGB) or (alpha shl ALPHA_SHIFT)

    private const val SHADE_ALPHA = 0xF2
    private const val LINE_ALPHA = 0x1F
    private const val RGB = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24
}

/** The overlays take the launcher's theme and accent as [appearance] has them, "as the system" as the device is set. */
internal fun followTheme(context: Context, appearance: Flow<HomeAppearance>, scope: CoroutineScope) {
    appearance.onEach { look ->
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val theme = Themes.forSetting(look.theme, systemDark = mode == Configuration.UI_MODE_NIGHT_YES)
        OverlayPalette.use(theme, look.accent.argb.takeIf { look.accentChosen })
    }.launchIn(scope)
}

/** Sizes the overlays share, so a hint or a gap is the same on each. */
internal object OverlayDimens {
    /** A hint's text: what the keys do, a note under a title. */
    const val HINT_SP = 12f

    /** Between a panel's rows or cards. */
    const val GAP_DP = 12
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

/** Opens Lumbina on [page]: one of the pages `LumbinaActions` names, a settings page or a place on home. */
internal fun Context.openLumbina(page: String) {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        .putExtra(LumbinaActions.EXTRA_SETTINGS_PAGE, page)
    startActivity(intent)
}

/** Lumbina's home screen, as its icon opens it. */
internal fun Context.openLumbinaHome() {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.rounded(color: Int, cornerDp: Int) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = cornerDp * resources.displayMetrics.density
}

internal fun Context.label(text: String, sp: Float, color: Int = OverlayPalette.text) = TextView(this).apply {
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
