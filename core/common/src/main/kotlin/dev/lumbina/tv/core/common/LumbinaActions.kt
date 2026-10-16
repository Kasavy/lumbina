package dev.lumbina.tv.core.common

/** In-app broadcast actions; sent with the app's own package, received by non-exported receivers. */
object LumbinaActions {
    /** A speech model was installed or deleted: voice input checks which languages it has. */
    const val SPEECH_MODELS_CHANGED = "dev.lumbina.tv.action.SPEECH_MODELS_CHANGED"

    /** An on-screen mic key was pressed: the accessibility service starts or stops voice input. */
    const val TOGGLE_VOICE = "dev.lumbina.tv.action.TOGGLE_VOICE"

    /** The settings ask the remote's service for the next button pressed ([EXTRA_CAPTURE_ON]), or stop asking. */
    const val CAPTURE_KEY = "dev.lumbina.tv.action.CAPTURE_KEY"
    const val EXTRA_CAPTURE_ON = "dev.lumbina.tv.extra.CAPTURE_ON"

    /** The service's answer: the button pressed, by its id ([EXTRA_KEY]). */
    const val KEY_CAPTURED = "dev.lumbina.tv.action.KEY_CAPTURED"
    const val EXTRA_KEY = "dev.lumbina.tv.extra.KEY"

    /**
     * The settings ask the service to tell the gestures made on one button ([EXTRA_KEY]) and not do
     * what they do; with no key, stop. The answers: [KEY_GESTURE] with the gesture's name ([EXTRA_GESTURE]).
     */
    const val PROBE_KEY = "dev.lumbina.tv.action.PROBE_KEY"
    const val KEY_GESTURE = "dev.lumbina.tv.action.KEY_GESTURE"
    const val EXTRA_GESTURE = "dev.lumbina.tv.extra.GESTURE"

    /** Opens Lumbina's screensaver (an activity), started by the accessibility service when idle. */
    const val SHOW_SCREENSAVER = "dev.lumbina.tv.action.SHOW_SCREENSAVER"

    /** Extra on Lumbina's launch intent: open its settings at this page (a `SettingsPage` name). */
    const val EXTRA_SETTINGS_PAGE = "dev.lumbina.tv.extra.SETTINGS_PAGE"

    /** Extra on Lumbina's launch intent: home starts again from its first tile. */
    const val EXTRA_TO_START = "dev.lumbina.tv.extra.TO_START"

    /** [EXTRA_SETTINGS_PAGE] values for the pages opened from outside the settings. */
    const val SETTINGS_PAGE_ROWS = "Rows"

    /** Not a page of the settings at all: the "All apps" screen, over home. */
    const val HOME_ALL_APPS = "AllApps"

    /** Not pages either: the home screen with one of its windows open over it. */
    const val HOME_SEARCH = "Search"
    const val HOME_SLEEP_TIMER = "SleepTimer"
    const val HOME_NOTICES = "Notices"
    val HOME_WINDOWS = setOf(HOME_SEARCH, HOME_SLEEP_TIMER, HOME_NOTICES)

    /** Not a page: the settings' own first list. */
    const val SETTINGS_ROOT = "Root"
    const val SETTINGS_PAGE_PHONE = "Phone"
    const val SETTINGS_PAGE_ACCESS = "Access"
    const val SETTINGS_PAGE_VOICE = "Voice"
    const val SETTINGS_PAGE_STATE = "State"
    const val SETTINGS_PAGE_WIDGETS = "Widgets"
}

/** Lumbina's components that other parts name without seeing their classes. */
object LumbinaComponents {
    /** The notification listener's class (in `:platform:system`, where a test holds the two together). */
    const val NOTIFICATION_LISTENER = "dev.lumbina.tv.platform.system.LumbinaNotificationListener"
}
