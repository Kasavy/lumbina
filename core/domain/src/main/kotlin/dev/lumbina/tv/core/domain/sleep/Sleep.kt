package dev.lumbina.tv.core.domain.sleep

import dev.lumbina.tv.core.domain.MILLIS_PER_SECOND
import dev.lumbina.tv.core.domain.settings.SettingsRepository

/**
 * When the device goes to standby by itself: [autoSleepMinutes] after the last remote button
 * press (null: never), and at [timerEndsAt] (wall-clock millis) when a sleep timer runs.
 * [screensaverMinutes]: idle time before Lumbina shows its screensaver over its own screens (null: never).
 * Shared by the launcher UI (which sets them) and the process that watches the remote.
 */
data class SleepSettings(
    val autoSleepMinutes: Int? = DEFAULT_AUTO_SLEEP_MINUTES,
    val timerEndsAt: Long? = null,
    val screensaverMinutes: Int? = DEFAULT_SCREENSAVER_MINUTES,
    /**
     * Screensaver photos from picsum.photos and Bing, which see the device's address, when the
     * user's own aren't the wallpaper.
     */
    val onlinePhotos: Boolean = true,
) {
    companion object {
        const val DEFAULT_AUTO_SLEEP_MINUTES = 180
        const val DEFAULT_SCREENSAVER_MINUTES = 10
    }
}

/** Sleep timer lengths to choose from, in minutes. */
val SLEEP_TIMER_MINUTES = listOf(15, 30, 60, 90, 120)

/**
 * Where OK takes a sleep timer with [minutesLeft] (null: off) when it steps through the
 * choices: the next longer length, and off after the longest.
 */
fun nextSleepTimer(minutesLeft: Int?): Int? =
    SLEEP_TIMER_MINUTES.firstOrNull { minutesLeft == null || it > minutesLeft }

/**
 * A step back along the same choices from a timer with [minutesLeft]: the next shorter length, and
 * off (null) before the shortest.
 */
fun previousSleepTimer(minutesLeft: Int): Int? = SLEEP_TIMER_MINUTES.lastOrNull { it < minutesLeft }

/** Whole minutes (rounded up) until the sleep timer ends, or null when none runs. */
fun SleepSettings.timerMinutesLeft(now: Long): Int? =
    timerEndsAt?.let { ((it - now + MINUTE_MILLIS - 1) / MINUTE_MILLIS).toInt() }?.takeIf { it > 0 }

/** A sleep timer ending [minutes] after [now]; null switches it off. */
fun SleepSettings.withTimer(minutes: Int?, now: Long): SleepSettings =
    copy(timerEndsAt = minutes?.let { now + it * MINUTE_MILLIS })

private const val MINUTE_MILLIS = 60_000L

typealias SleepSettingsRepository = SettingsRepository<SleepSettings>

/** Puts the device into standby, like its power button. False if the device doesn't allow it. */
fun interface Standby {
    fun sleep(): Boolean
}

enum class SleepReason { Timer, Idle }

sealed interface SleepDecision {
    data object Stay : SleepDecision

    /** Standby is [secondsLeft] away; any button press should call it off. */
    data class Warn(val secondsLeft: Int, val reason: SleepReason) : SleepDecision

    data class Sleep(val reason: SleepReason) : SleepDecision
}

/**
 * Decides when to go to standby from the remote's activity and the [SleepSettings]; warns
 * [warnMillis] before. Button presses ([onActivity]) postpone the idle deadline. A timer that ran
 * out more than [STALE_TIMER_MILLIS] ago is ignored: the device already slept (or the process was
 * gone), and it must not send the device back to standby right after waking up.
 */
class SleepClock(private val now: () -> Long, private val warnMillis: Long = DEFAULT_WARN_MILLIS) {
    /** How long the warning shows before standby, in seconds. */
    val warnSeconds: Int get() = (warnMillis / MILLIS_PER_SECOND).toInt()

    var lastActivity: Long = now()
        private set

    fun onActivity() {
        lastActivity = now()
    }

    /**
     * False while the screen is off: there is nothing to put to standby, and the time doesn't
     * count as idle. The screen coming on counts as a button press, since waking the device
     * with the power button doesn't reach [onActivity].
     */
    var screenOn: Boolean = true
        private set

    fun onScreen(on: Boolean) {
        if (on && !screenOn) onActivity()
        screenOn = on
    }

    /** True once the remote has been idle for the screensaver delay. */
    fun screensaverDue(settings: SleepSettings): Boolean =
        screenOn && settings.screensaverMinutes?.let { now() - lastActivity >= it * MILLIS_PER_MINUTE } ?: false

    fun decide(settings: SleepSettings): SleepDecision {
        if (!screenOn) return SleepDecision.Stay
        val deadlines = listOfNotNull(
            settings.timerEndsAt?.takeIf { now() - it < STALE_TIMER_MILLIS }?.let { it to SleepReason.Timer },
            settings.autoSleepMinutes?.let { lastActivity + it * MILLIS_PER_MINUTE to SleepReason.Idle },
        )
        val (at, reason) = deadlines.minByOrNull { it.first } ?: return SleepDecision.Stay
        val left = at - now()
        return when {
            left <= 0 -> SleepDecision.Sleep(reason)

            left <= warnMillis -> SleepDecision.Warn(
                ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt(),
                reason,
            )

            else -> SleepDecision.Stay
        }
    }

    /**
     * Millis until [decide] or [screensaverDue] can next give a different answer by time alone:
     * a warning starting, each second of its countdown, standby, a finished timer going stale,
     * the screensaver delay. [Long.MAX_VALUE] when nothing is ahead; new settings and button
     * presses ([onActivity]) change the answer too, so callers ask again after those.
     */
    fun nextCheckIn(settings: SleepSettings): Long {
        if (!screenOn) return Long.MAX_VALUE
        val now = now()
        val timer = settings.timerEndsAt?.takeIf { now - it < STALE_TIMER_MILLIS }?.let { endsAt ->
            untilChange(endsAt - now) ?: (endsAt + STALE_TIMER_MILLIS - now)
        }
        val idle = settings.autoSleepMinutes?.let { untilChange(lastActivity + it * MILLIS_PER_MINUTE - now) }
        val screensaver = settings.screensaverMinutes
            ?.let { lastActivity + it * MILLIS_PER_MINUTE - now }
            ?.takeIf { it > 0 }
        return listOfNotNull(timer, idle, screensaver).minOrNull() ?: Long.MAX_VALUE
    }

    /** For a deadline [left] millis away: when its warning starts or its countdown ticks; null once passed. */
    private fun untilChange(left: Long): Long? = when {
        left > warnMillis -> left - warnMillis
        left > 0 -> (left - 1) % MILLIS_PER_SECOND + 1
        else -> null
    }

    companion object {
        private const val STALE_TIMER_MILLIS = 30_000L
        private const val DEFAULT_WARN_MILLIS = 60_000L
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
