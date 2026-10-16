package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.MILLIS_PER_SECOND
import dev.lumbina.tv.core.domain.sleep.SleepClock
import dev.lumbina.tv.core.domain.sleep.SleepDecision
import dev.lumbina.tv.core.domain.sleep.SleepReason
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.sleep.Standby
import dev.lumbina.tv.core.domain.sleep.withTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sleep timer and auto-sleep: sees every remote button press (the service filters all keys),
 * warns a minute before with a card in the corner ([onWarningKey]), and puts the device to
 * standby like its power button. Also starts Lumbina's screensaver when the remote is idle while
 * one of Lumbina's own screens is in front, so it never covers a video. Sleeps until the next
 * moment something can change ([SleepClock.nextCheckIn]), or until new settings, a button press,
 * the screen going off or on, or (while the screensaver waits for Lumbina to come to the front) a
 * window change. Time with the screen off isn't idle time: the firmware kills and restarts this
 * process at standby, and hours of standby must not put the device straight back to sleep after
 * a wake. Main thread.
 */
internal class SleepWatcher(
    private val service: AccessibilityService,
    private val repository: SleepSettingsRepository,
    private val standby: Standby,
    private val log: LumbinaLog,
    private val clock: SleepClock = SleepClock(now = System::currentTimeMillis),
) {
    private val overlay = CornerCard(service)

    /** The warning's card: another card of the service stands over it. */
    val card: CornerCard get() = overlay

    /** Whether the warning shows now: it has every key then. */
    val warns: Boolean get() = warning != null
    private var settings = SleepSettings()
    private var warning: SleepReason? = null
    private var dismissedBy: Int? = null
    private var scope: CoroutineScope? = null
    private var screensaverShownFor: Long? = null
    private var screensaverWaits = false
    private val recheck = Channel<Unit>(Channel.CONFLATED)
    private val power = service.getSystemService(PowerManager::class.java)

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> clock.onScreen(on = false)
                Intent.ACTION_SCREEN_ON -> clock.onScreen(on = true)
            }
            recheck()
        }
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(service, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            repository.settings.collect {
                settings = it
                recheck()
            }
        }
        scope.launch {
            while (true) {
                check()
                val wait = clock.nextCheckIn(settings).coerceAtMost(MAX_WAIT_MILLIS)
                withTimeoutOrNull(wait) { recheck.receive() }
            }
        }
    }

    /** Every button press counts as activity; while the warning shows, keys go to it (press and release). */
    fun onKey(event: KeyEvent): Boolean {
        clock.onActivity()
        recheck()
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == dismissedBy) {
            dismissedBy = null
            return true
        }
        val reason = warning ?: return false
        if (event.action == KeyEvent.ACTION_DOWN) onWarningKey(reason, event)
        return true
    }

    /**
     * ◀ ▶ pick a button, OK presses it; any other key means "+15 minutes" (or "still here" before
     * auto-sleep), so a stray press never turns the device off or loses the timer.
     */
    private fun onWarningKey(reason: SleepReason, event: KeyEvent) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> overlay.move(-1)

            KeyEvent.KEYCODE_DPAD_RIGHT -> overlay.move(1)

            else -> {
                val timerOff = reason == SleepReason.Timer && event.isOk && overlay.selected == TIMER_OFF
                dismissedBy = event.keyCode
                warning = null
                overlay.hide()
                if (reason == SleepReason.Timer) extendOrStop(timerOff) else log.log(TAG, "auto-sleep called off")
                // What the press did is said: the card that asked is gone with it.
                val done = when {
                    reason != SleepReason.Timer -> null
                    timerOff -> service.getString(R.string.sleep_timer_cancelled)
                    else -> service.getString(R.string.sleep_in_minutes, EXTEND_MINUTES)
                }
                done?.let { overlay.flash(it, DONE_MILLIS) }
            }
        }
    }

    private fun extendOrStop(stop: Boolean) {
        log.log(TAG, if (stop) "sleep timer off" else "sleep timer +$EXTEND_MINUTES min")
        val minutes = if (stop) null else EXTEND_MINUTES
        val now = System.currentTimeMillis()
        val change = { current: SleepSettings -> current.withTimer(minutes, now) }
        // Right away too: the next tick must not act on the old timer before the store answers.
        settings = change(settings)
        scope?.launch { repository.update(change) }
    }

    /** Another window came to the front: maybe Lumbina's, where a due screensaver can now show. */
    fun onWindowChanged() {
        if (screensaverWaits) recheck()
    }

    fun stop() {
        service.unregisterReceiver(screen)
        overlay.hide()
    }

    private fun recheck() {
        recheck.trySend(Unit)
    }

    /** Debug builds: starts a sleep timer of [seconds], so the warning and standby can be tried quickly. */
    fun debugSleepIn(seconds: Int) {
        val endsAt = System.currentTimeMillis() + seconds * MILLIS_PER_SECOND
        scope?.launch { repository.update { it.copy(timerEndsAt = endsAt) } }
    }

    private fun check() {
        // Also here: after a wake the check may come before the screen-on broadcast.
        power?.let { clock.onScreen(it.isInteractive) }
        when (val decision = clock.decide(settings)) {
            SleepDecision.Stay -> {
                if (warning != null) {
                    warning = null
                    overlay.hide()
                }
                maybeShowScreensaver()
            }

            is SleepDecision.Warn -> {
                warning = decision.reason
                val buttons = if (decision.reason == SleepReason.Timer) {
                    listOf(
                        service.resources.getQuantityString(R.plurals.sleep_extend, EXTEND_MINUTES, EXTEND_MINUTES),
                        service.getString(R.string.sleep_timer_off),
                    )
                } else {
                    listOf(service.getString(R.string.sleep_stay))
                }
                overlay.show(
                    message = service.getString(R.string.sleep_warning, decision.secondsLeft),
                    fraction = decision.secondsLeft.toFloat() / clock.warnSeconds,
                    buttons = buttons,
                    hint = if (decision.reason == SleepReason.Timer) {
                        service.resources.getQuantityString(R.plurals.sleep_any_extends, EXTEND_MINUTES, EXTEND_MINUTES)
                    } else {
                        service.getString(R.string.sleep_any_cancels)
                    },
                )
            }

            is SleepDecision.Sleep -> sleep(decision.reason)
        }
    }

    private fun maybeShowScreensaver() {
        val due = clock.screensaverDue(settings) && screensaverShownFor != clock.lastActivity
        screensaverWaits = due && service.rootInActiveWindow?.packageName != service.packageName
        if (!due || screensaverWaits) return
        screensaverShownFor = clock.lastActivity
        val intent = Intent(LumbinaActions.SHOW_SCREENSAVER)
            .setPackage(service.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { service.startActivity(intent) }
            .onFailure { log.log(TAG, "screensaver: $it") }
    }

    private fun sleep(reason: SleepReason) {
        warning = null
        overlay.hide()
        clock.onActivity() // no second standby before the screen goes off
        scope?.launch {
            // Standby may kill this process, so the timer is cleared before, not after.
            if (reason == SleepReason.Timer) repository.update { it.copy(timerEndsAt = null) }
            val done = standby.sleep()
            log.log(TAG, "standby ($reason): ${if (done) "ok" else "not allowed on this device"}")
            if (!done) overlay.flash(service.getString(R.string.sleep_refused), REFUSED_MILLIS)
        }
    }

    private companion object {
        const val TAG = "sleep"

        /** A safety net: delays don't count deep sleep, and the wall clock can be set. */
        const val MAX_WAIT_MILLIS = 60_000L
        const val EXTEND_MINUTES = 15

        /** How long the card says what a press did, and that the device would not turn off. */
        const val DONE_MILLIS = 2_500L
        const val REFUSED_MILLIS = 5_000L

        /** The second button of the timer's warning. */
        const val TIMER_OFF = 1
    }
}
