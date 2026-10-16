package dev.lumbina.tv.baselineprofile

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
private const val SETUP_MILLIS = 10_000L

private const val REST_MILLIS = 300L

// LumbinaActions.EXTRA_SETTINGS_PAGE and SETTINGS_ROOT: this module sees the app only from outside.
private const val SETTINGS_PAGE_EXTRA = "dev.lumbina.tv.extra.SETTINGS_PAGE"
private const val SETTINGS_ROOT = "Root"
private const val HOME_ALL_APPS = "AllApps"

// The journeys find their way by what the screen says, in English.
private const val ALL_APPS = "All apps"
private const val APPS_FIND = "Find"
private const val MENU_ENTRY = "Where to show…"
private const val SETTINGS_TITLE = "Settings"
private const val SKIP_SETUP = "Skip setup"
private const val ROWS_PAGE = "Rows"

/** The pages of the settings by the names links from outside open them by: SettingsPage's entries. */
private val SETTINGS_PAGES = listOf(
    ROWS_PAGE,
    "Widgets",
    "Channels",
    "Wallpaper",
    "Look",
    "HomeKey",
    "Voice",
    "Phone",
    "Sleep",
    "Access",
    "State",
    "About",
)

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

/**
 * The first-run setup stands over everything on a fresh install: leave it by "Skip setup", as a
 * user in a hurry would. Nothing to do where it does not show.
 */
internal fun MacrobenchmarkScope.skipSetup() {
    // It comes a few seconds after the first frame, once Lumbina knows what is left to set up.
    if (device.wait(Until.hasObject(By.text(SKIP_SETUP)), SETUP_MILLIS) != true) return
    // The focus starts on the page's first button; "Skip setup" is the one thing above it.
    Thread.sleep(REST_MILLIS)
    device.pressDPadUp()
    device.pressDPadCenter()
    check(device.wait(Until.gone(By.text(SKIP_SETUP)), FIND_MILLIS) == true) { "the setup did not close" }
    device.waitForIdle()
}

/**
 * "All apps", a row of home, asked for the way the quick menu does (the row's name is on screen
 * twice, on its shortcut and over the row, so it is no label to walk to); then down its grid, a tile's
 * menu, one of the heading's dialogs, and Back twice, to the row's start and to the top of home.
 */
internal fun MacrobenchmarkScope.visitAllApps() {
    device.executeShellCommand("am start -n $PACKAGE/.MainActivity --es $SETTINGS_PAGE_EXTRA $HOME_ALL_APPS")
    checkNotNull(device.wait(Until.findObject(By.text(APPS_FIND)), FIND_MILLIS)) {
        "\"$ALL_APPS\" didn't come into view"
    }
    device.waitForIdle()
    repeat(GRID_STEPS) {
        device.pressDPadDown()
        device.waitForIdle()
    }
    check(openTileMenu()) { "no menu on a tile of \"$ALL_APPS\"" }
    repeat(GRID_STEPS + 1) { device.pressDPadUp() } // focus ends on the heading's buttons
    device.pressDPadCenter()
    device.waitForIdle()
    device.pressBack()
    device.pressBack()
    device.pressBack()
    device.waitForIdle()
}

/**
 * Settings. First their list, opened the way the quick menu does (the capsule's button can't be
 * found by a label): down it, a page opened from it and left by Back. Then every page by its name,
 * the way a link from outside opens one, and down each; in "Rows" one row's own page too. A page
 * this device has no use for opens the list instead, which costs nothing. Back twice, home again.
 */
internal fun MacrobenchmarkScope.visitSettings() {
    openSettings(SETTINGS_ROOT)
    repeat(SETTINGS_STEPS) {
        device.pressDPadDown()
        device.waitForIdle()
    }
    device.pressDPadCenter()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
    SETTINGS_PAGES.forEach { page ->
        openSettings(page)
        if (page == ROWS_PAGE) visitRowPage()
        repeat(SETTINGS_STEPS) {
            device.pressDPadDown()
            device.waitForIdle()
        }
    }
    device.pressBack()
    device.pressBack()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.openSettings(page: String) {
    device.executeShellCommand("am start -n $PACKAGE/.MainActivity --es $SETTINGS_PAGE_EXTRA $page")
    checkNotNull(device.wait(Until.findObject(By.text(SETTINGS_TITLE)), FIND_MILLIS)) {
        "settings didn't open at $page"
    }
    device.waitForIdle()
    // The focus is placed a few frames after the page is there.
    Thread.sleep(REST_MILLIS)
}

/** From the list of rows: the second entry is Favorites; its page, down it, and back to the list. */
private fun MacrobenchmarkScope.visitRowPage() {
    device.pressDPadDown()
    device.pressDPadCenter()
    device.waitForIdle()
    repeat(SETTINGS_STEPS) {
        device.pressDPadDown()
        device.waitForIdle()
    }
    device.pressBack()
    device.waitForIdle()
}
