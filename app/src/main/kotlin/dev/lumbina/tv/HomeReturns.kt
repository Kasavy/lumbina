package dev.lumbina.tv

/**
 * Go to the home screen, or on to Lumbina's settings at [settingsPage]. A new one each time, so
 * the same request made twice is acted on twice.
 */
internal class NavRequest(val settingsPage: String?, val onScreen: Boolean = false, val toStart: Boolean = false) {
    /**
     * Home goes to its first tile: when asked to ([toStart]), or when Home was pressed with home
     * itself on screen ([onScreen] and [onHome]). Otherwise it goes back to the element it was
     * left on: back from an app, from settings or all apps.
     */
    fun sendsHomeToStart(onHome: Boolean): Boolean = toStart || (onScreen && onHome)
}

/**
 * When coming back to Lumbina sends home to its first tile, from the activity's lifecycle: after
 * a long absence, when the process was killed meanwhile, on power-on ([newIntent]'s
 * `toStartAsked`), and on Home pressed on home itself. Plain Kotlin; times are elapsed millis.
 */
internal class HomeReturns(private val longAbsenceMillis: Long = LONG_ABSENCE_MILLIS) {
    /** When Lumbina last left the screen; null once it is back (resumed). */
    private var stoppedAt: Long? = null

    /** Back after a long absence: set on starting, until resumed. */
    private var longAway = false

    /**
     * The activity was created: [restored] from saved state, in a [freshProcess] (none of this
     * process's activities before it), launched for [settingsPage].
     */
    fun created(restored: Boolean, freshProcess: Boolean, settingsPage: String?): NavRequest? = when {
        !restored -> settingsPage?.let { NavRequest(it) }

        // The saved state is from before the process was killed.
        freshProcess -> NavRequest(settingsPage = null, toStart = true)

        // Re-created (e.g. a language change): the screen that was open stays.
        else -> null
    }

    fun started(now: Long): NavRequest? {
        val away = stoppedAt?.let { now - it }
        longAway = away != null && away > longAbsenceMillis
        return if (longAway) NavRequest(settingsPage = null, toStart = true) else null
    }

    // Not on starting: a new intent comes after onStart, and must still see Lumbina was away.
    fun resumed() {
        stoppedAt = null
        longAway = false
    }

    fun stopped(now: Long) {
        stoppedAt = now
    }

    /** Lumbina was opened again: Home, the quick menu, power-on. Stopped means back from another app. */
    fun newIntent(settingsPage: String?, toStartAsked: Boolean): NavRequest =
        NavRequest(settingsPage, onScreen = stoppedAt == null, toStart = longAway || toStartAsked)

    private companion object {
        const val LONG_ABSENCE_MILLIS = 60 * 60_000L
    }
}
