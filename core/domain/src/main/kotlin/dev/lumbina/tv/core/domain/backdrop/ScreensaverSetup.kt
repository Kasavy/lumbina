package dev.lumbina.tv.core.domain.backdrop

/**
 * What to run on a computer (adb) where Lumbina may not make [component] the system screensaver
 * itself: the two settings a [ScreensaverSetup] writes.
 */
fun screensaverCommands(component: String): List<String> = listOf(
    "adb shell settings put secure screensaver_components $component",
    "adb shell settings put secure screensaver_enabled 1",
)

/** Whether Lumbina is the system screensaver, and making it so where the device allows. */
interface ScreensaverSetup {
    /** True when the system starts Lumbina's screensaver after idle time. */
    val isLumbina: Boolean

    /** Makes Lumbina the system screensaver; false without the permission to change it. */
    fun select(): Boolean

    /**
     * Where the user can choose Lumbina by hand, with no permission: the device's own list of
     * screensavers. Null where the device has none (Vidda; Google TV keeps only its own).
     */
    val picker: ScreensaverPicker?

    /** Opens the device's list ([picker]); false when it has none or nothing opened. */
    fun openPicker(): Boolean
}

/** How the device's own list of screensavers is reached. */
enum class ScreensaverPicker {
    /** Its screen opens directly. */
    Screen,

    /** Only through the settings' main screen: the user goes on to it ("Device Preferences", "Screen saver"). */
    InSettings,
}

/**
 * Where the user can choose Lumbina's screensaver by hand. [screenOpens]: the screensaver screen's
 * own action leads somewhere real (newer Android TV). [tvSettings]: the main settings are Android
 * TV's own, which keep the list under "Device Preferences" (Android TV 9, Mi Box 4); firmware's own
 * settings list only the firmware's screensavers (Vidda). Google TV offers only Google's: nowhere.
 */
fun screensaverPicker(screenOpens: Boolean, tvSettings: Boolean, googleTv: Boolean): ScreensaverPicker? = when {
    googleTv -> null
    screenOpens -> ScreensaverPicker.Screen
    tvSettings -> ScreensaverPicker.InSettings
    else -> null
}
