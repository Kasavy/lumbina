package dev.lumbina.tv.core.common

/** In-app broadcast actions; sent with the app's own package, received by non-exported receivers. */
object LumbinaActions {
    /** A speech model was installed or deleted: voice input checks which languages it has. */
    const val SPEECH_MODELS_CHANGED = "dev.lumbina.tv.action.SPEECH_MODELS_CHANGED"

    /** An on-screen mic key was pressed: the accessibility service starts or stops voice input. */
    const val TOGGLE_VOICE = "dev.lumbina.tv.action.TOGGLE_VOICE"

    /** Opens Lumbina's screensaver (an activity), started by the accessibility service when idle. */
    const val SHOW_SCREENSAVER = "dev.lumbina.tv.action.SHOW_SCREENSAVER"

    /** Extra on Lumbina's launch intent: open its settings at this page (a `SettingsPage` name). */
    const val EXTRA_SETTINGS_PAGE = "dev.lumbina.tv.extra.SETTINGS_PAGE"

    /** Extra on Lumbina's launch intent: home starts again from its first tile. */
    const val EXTRA_TO_START = "dev.lumbina.tv.extra.TO_START"

    /** [EXTRA_SETTINGS_PAGE] values for the pages opened from outside the settings. */
    const val SETTINGS_PAGE_ROWS = "Rows"
    const val SETTINGS_PAGE_PHONE = "Phone"
}

/** Lumbina's components that other parts name without seeing their classes. */
object LumbinaComponents {
    /** The notification listener's class (in `:platform:system`, where a test holds the two together). */
    const val NOTIFICATION_LISTENER = "dev.lumbina.tv.platform.system.LumbinaNotificationListener"
}
