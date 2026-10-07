package dev.lumbina.tv.platform.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "When the device turns on, open Lumbina". Counts: the screen coming on after going off, and the
 * service connecting while the screen is on and the stock launcher is in front. The latter is a
 * boot (the firmware starts its own launcher, whatever app has the home role) or a wake the
 * service didn't see, having been killed on standby and brought back. For a while after either,
 * the firmware may bring its launcher up again over Lumbina; it gets Lumbina over it again, a limited
 * number of times. Main thread.
 */
internal class PowerOnWatcher(
    private val context: Context,
    private val settings: AccessSettingsRepository,
    /** The firmware's launcher, which may come up over Lumbina after a power-on. */
    private val stockLauncher: String?,
    /** The package of the app in front now, if the service can tell. */
    private val frontPackage: () -> String?,
    private val open: () -> Unit,
    private val log: LumbinaLog,
) {
    private var screenWentOff = false
    private var scope: CoroutineScope? = null

    /** Until then after a power-on, the stock launcher coming to the front is covered by Lumbina again. */
    private var coverUntil = 0L
    private var coversLeft = 0
    private var lastCover = 0L

    /** Until then the windows coming to the front are logged, each once ([noted]). */
    private var noteUntil = 0L
    private val noted = HashSet<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenWentOff = true

                Intent.ACTION_SCREEN_ON -> if (screenWentOff) {
                    screenWentOff = false
                    powerOn("screen on")
                }
            }
        }
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive == true
        // Started with the screen off (brought back while the device goes to standby): the next
        // time it comes on is a wake, though this process never saw it go off.
        screenWentOff = !interactive
        // Asking who is in front waits for that app's answer: only where the answer decides something.
        val front = if (interactive && stockLauncher != null) frontPackage() else null
        val sinceBoot = SystemClock.elapsedRealtime() / MILLIS_PER_SECOND
        val state = if (interactive) "screen on, ${front ?: "front not asked"}" else "screen off"
        log.log(TAG, "connected ${sinceBoot}s after boot: $state")
        noteUntil = SystemClock.elapsedRealtime() + NOTE_MILLIS
        if (front != null && front == stockLauncher) {
            powerOn("connected over the stock launcher")
        }
    }

    /**
     * A window ([className] of [packageName]) came to the front: the stock launcher's, soon after a
     * power-on, gets Lumbina over it again.
     */
    fun onWindow(packageName: CharSequence?, className: CharSequence?) {
        note(packageName, className)
        if (coversLeft == 0 || stockLauncher == null || packageName?.toString() != stockLauncher) return
        val now = SystemClock.elapsedRealtime()
        if (now >= coverUntil) {
            coversLeft = 0
            return
        }
        // One window reports several state changes.
        if (now - lastCover < COVER_GAP_MILLIS) return
        lastCover = now
        coversLeft--
        openIfWanted("stock launcher came up after power-on")
    }

    fun stop() {
        context.unregisterReceiver(receiver)
    }

    /**
     * For a minute after the service connects or a power-on, logs each window that comes to the
     * front: a home screen of the firmware that is not [stockLauncher] shows up here. The class is
     * the activity's for an app's main window, and a view's for some others.
     */
    private fun note(packageName: CharSequence?, className: CharSequence?) {
        if (SystemClock.elapsedRealtime() >= noteUntil) return
        val window = "$packageName/$className"
        if (noted.add(window)) log.log(TAG, "in front: $window")
    }

    private fun powerOn(why: String) {
        val now = SystemClock.elapsedRealtime()
        noteUntil = now + NOTE_MILLIS
        noted.clear()
        coverUntil = now + COVER_MILLIS
        coversLeft = COVERS
        // The stock launcher's window may still be reporting in; that isn't it coming up again.
        lastCover = now
        openIfWanted(why)
    }

    private fun openIfWanted(why: String) {
        scope?.launch {
            if (!settings.settings.first().openOnPowerOn) {
                log.log(TAG, "$why: set to open nothing")
                return@launch
            }
            log.log(TAG, "$why: opening Lumbina")
            open()
        }
    }

    private companion object {
        const val TAG = "power"
        const val COVER_MILLIS = 10_000L
        const val COVERS = 2
        const val COVER_GAP_MILLIS = 1_000L
        const val NOTE_MILLIS = 60_000L
        const val MILLIS_PER_SECOND = 1_000
    }
}
