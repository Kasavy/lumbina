package dev.lumbina.tv.baselineprofile

import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.Locale

internal const val PACKAGE = "dev.lumbina.tv"

private const val GRID_STEPS = 6
private const val ROW_STEPS = 6
private const val SETTINGS_STEPS = 5
private const val FIND_MILLIS = 3_000L
private const val STEP_MILLIS = 500L
private const val FOCUS_STEPS = 20
private const val ROWS_PAGE_STEPS = 9

// As many as the settings have: SettingsSection's entries, and the most tabs a section shows.
private const val SETTINGS_SECTIONS = 5
private const val TAB_STEPS = 3
private const val REST_MILLIS = 300L
private const val POLL_MILLIS = 50L

// LumbinaActions.EXTRA_SETTINGS_PAGE and SETTINGS_PAGE_ROWS: this module sees the app only from outside.
private const val SETTINGS_PAGE_EXTRA = "dev.lumbina.tv.extra.SETTINGS_PAGE"
private const val ROWS_PAGE = "Rows"

// The journeys find their way by what the screen says, in English.
private const val ALL_APPS = "All apps"
private const val MENU_ENTRY = "Where to show…"
private const val SETTINGS_TITLE = "Settings"

/** The journeys read the screen in English: on a device in another language they would wander. */
internal fun requireEnglish() {
    val language = Locale.getDefault().language
    check(language == "en") { "set the device to English for the journeys (it is \"$language\")" }
}

/** Before the first start: the system's prompt for other apps' channels would take the first key presses. */
internal fun MacrobenchmarkScope.allowTvListings() {
    device.executeShellCommand("pm grant $PACKAGE android.permission.READ_TV_LISTINGS")
}

/** Down through the rows and back up, along two of them on the way, as a remote scrolls them. */
internal fun MacrobenchmarkScope.browseHome() {
    repeat(GRID_STEPS) { step ->
        device.pressDPadDown()
        device.waitForIdle()
        if (step < 2) browseRow()
    }
    repeat(GRID_STEPS) {
        device.pressDPadUp()
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.browseRow() {
    repeat(ROW_STEPS) {
        device.pressDPadRight()
        device.waitForIdle()
    }
    repeat(ROW_STEPS) {
        device.pressDPadLeft()
        device.waitForIdle()
    }
}

/**
 * The menu of what has the focus (a long press of OK), a step into it, and out. False when no
 * menu opened (the focus was not on a tile): then nothing more is pressed.
 */
internal fun MacrobenchmarkScope.openTileMenu(): Boolean {
    device.executeShellCommand("input keyevent --longpress ${KeyEvent.KEYCODE_DPAD_CENTER}")
    if (device.wait(Until.hasObject(By.text(MENU_ENTRY)), FIND_MILLIS) != true) return false
    device.pressDPadDown()
    device.pressBack()
    device.waitForIdle()
    return true
}

/** "All apps" from home: its grid, a tile's menu, one of the header's dialogs, and back home. */
internal fun MacrobenchmarkScope.visitAllApps() {
    check(openByLabel(ALL_APPS)) { "the \"$ALL_APPS\" tile can't be reached on home" }
    device.waitForIdle()
    repeat(GRID_STEPS) {
        device.pressDPadDown()
        device.waitForIdle()
    }
    check(openTileMenu()) { "no menu on a tile of \"$ALL_APPS\"" }
    repeat(GRID_STEPS + 1) { device.pressDPadUp() } // focus ends on the header's buttons
    device.pressDPadCenter()
    device.waitForIdle()
    device.pressBack()
    device.pressBack()
    device.waitForIdle()
}

/**
 * Moves the focus with the d-pad onto what shows [label] and presses OK: tiles don't take
 * touches. False when the focus doesn't get there.
 */
private fun MacrobenchmarkScope.openByLabel(label: String): Boolean {
    repeat(FOCUS_STEPS) {
        // A node may go stale between finding it and reading it, while the screen scrolls: then try again.
        val target = runCatching {
            device.wait(Until.findObject(By.text(label)), STEP_MILLIS)?.visibleCenter
        }.getOrNull()
        val focused = runCatching { device.findObject(By.focused(true))?.visibleBounds }.getOrNull()
        when {
            // Not on screen yet (below the setup card, say), or nothing focused yet.
            target == null || focused == null -> device.pressDPadDown()

            focused.contains(target.x, target.y) -> {
                device.pressDPadCenter()
                return true
            }

            focused.bottom < target.y -> device.pressDPadDown()

            focused.top > target.y -> device.pressDPadUp()

            focused.right < target.x -> device.pressDPadRight()

            else -> device.pressDPadLeft()
        }
        awaitFocusMoved(focused)
    }
    return false
}

/**
 * Waits for the focus to leave [from] and the scroll that follows it to end: the device being idle
 * doesn't tell, and a press sent before the last one took would overshoot the target.
 */
private fun MacrobenchmarkScope.awaitFocusMoved(from: Rect?) {
    val until = SystemClock.uptimeMillis() + STEP_MILLIS
    while (SystemClock.uptimeMillis() < until) {
        val now = runCatching { device.findObject(By.focused(true))?.visibleBounds }.getOrNull()
        if (now != null && now != from) break
        Thread.sleep(POLL_MILLIS)
    }
    Thread.sleep(REST_MILLIS)
}

/**
 * Settings, opened the way the phone page's link does (the top bar's button can't be found by a
 * label): the rows page to its end, then every section, along its tabs and down the page of the
 * last of them, and back home.
 */
internal fun MacrobenchmarkScope.visitSettings() {
    device.executeShellCommand("am start -n $PACKAGE/.MainActivity --es $SETTINGS_PAGE_EXTRA $ROWS_PAGE")
    checkNotNull(device.wait(Until.findObject(By.text(SETTINGS_TITLE)), FIND_MILLIS)) { "settings didn't open" }
    device.waitForIdle()
    // Focus starts on the open section; the rows page is the longest, and the one to scroll.
    device.pressDPadRight()
    repeat(ROWS_PAGE_STEPS) {
        device.pressDPadDown()
        device.waitForIdle()
    }
    repeat(ROWS_PAGE_STEPS + 1) { device.pressDPadUp() }
    repeat(TAB_STEPS) { device.pressDPadLeft() } // from the page's tabs to the sections
    repeat(SETTINGS_SECTIONS) {
        rest()
        repeat(TAB_STEPS) {
            device.pressDPadRight() // along the tabs, or into a page that has none
            rest()
        }
        repeat(SETTINGS_STEPS) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(SETTINGS_STEPS + 1) { device.pressDPadUp() }
        repeat(TAB_STEPS + 1) { device.pressDPadLeft() } // back to the sections
        device.pressDPadDown() // the next section
    }
    device.pressBack()
    device.waitForIdle()
}

/** A section or a tab opens once the focus has rested on it. */
private fun MacrobenchmarkScope.rest() {
    device.waitForIdle()
    Thread.sleep(REST_MILLIS)
}
