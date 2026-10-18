package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import dev.lumbina.tv.core.common.LumbinaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Held volume buttons where the volume is Android's. While the service takes the buttons, a TV's
 * Android changes the volume on a volume key before the service sees it and never repeats the key:
 * a hold is one step. So once the user changes the volume (its bar shows, or the system says it
 * changed), the service lets the buttons go for [Timing.freeMillis], counted again at each change,
 * and the next hold repeats as without Lumbina. Meanwhile Lumbina's buttons do what the device's do.
 * Not while a screen of the service's own needs the keys ([busy]), nor for Lumbina's own volume
 * changes ([ownChange]); after [Timing.longestMillis] in a row the buttons are taken back for a
 * while. Runs on the [scope]'s thread, the main one.
 */
internal class VolumeHold(
    private val context: Context,
    private val scope: CoroutineScope,
    private val log: LumbinaLog,
    /** Lets the service take the remote's keys (true) or leaves them to the device. */
    private val filterKeys: (Boolean) -> Unit,
    private val timing: Timing = Timing(),
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    class Timing(
        val freeMillis: Long = 5_000,
        /** Let go this long in a row, the buttons are taken back, and stay so for [freeMillis]. */
        val longestMillis: Long = 30_000,
        /** A volume bar this soon after [ownChange] is Lumbina's own. */
        val ownMillis: Long = 1_500,
        /** How often a screen of the service's own is looked for while the buttons are let go. */
        val checkMillis: Long = 250,
    )

    /** The user's switch ([dev.lumbina.tv.core.domain.remote.RemoteSettings.volumeHold]). */
    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            listen(started && value)
            if (!value) takeBack()
        }

    /** Whether a screen of the service's own shows over the app and needs the remote's keys. */
    var busy: () -> Boolean = { false }

    private var started = false
    private var listening = false
    private var back: Job? = null
    private var freedAt = 0L
    private var ownAt = Long.MIN_VALUE / 2
    private var restUntil = Long.MIN_VALUE

    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_STREAM, -1) == AudioManager.STREAM_MUSIC) volumeChanged()
        }
    }

    fun start() {
        started = true
        listen(enabled)
    }

    fun stop() {
        started = false
        listen(false)
        takeBack()
    }

    /** Lumbina changes the volume itself (voice, the phone remote): the bar that shows is not a press. */
    fun ownChange() {
        ownAt = now()
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName == context.packageName) return
        val window = "${event.packageName}/${event.className}"
        if (VOLUME_WINDOWS.any { window.contains(it, ignoreCase = true) }) volumeChanged()
    }

    private fun volumeChanged() {
        val at = now()
        if (!enabled || busy()) return
        // Lumbina's own change, or the rest after too long a run.
        if (at - ownAt < timing.ownMillis || at < restUntil) return
        if (back == null) {
            freedAt = at
            filterKeys(false)
            log.log(TAG, "volume: buttons let go for a hold")
        }
        back?.cancel()
        val until = minOf(at + timing.freeMillis, freedAt + timing.longestMillis)
        back = scope.launch {
            while (now() < until && !busy()) delay(minOf(timing.checkMillis, until - now()))
            if (now() - freedAt >= timing.longestMillis) restUntil = now() + timing.freeMillis
            back = null
            filterKeys(true)
        }
    }

    private fun takeBack() {
        val job = back ?: return
        job.cancel()
        back = null
        filterKeys(true)
    }

    private fun listen(on: Boolean) {
        if (on == listening) return
        if (on) {
            val filter = IntentFilter(VOLUME_CHANGED)
            ContextCompat.registerReceiver(context, changes, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            context.unregisterReceiver(changes)
        }
        listening = on
    }

    private companion object {
        const val TAG = "a11y"

        /** Hidden in AudioManager; AudioService sends it when a stream's level changes (not a fixed one's). */
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        /**
         * The volume bar's window, by its package and class: AOSP's dialog and Xiaomi's
         * (`com.xiaomi.mitv.systemui.volume.VolumVerticalDialog`, seen on a Mi Box 4) under
         * `systemui.volume`, the old VolumePanel, a volume dialog app of its own.
         */
        val VOLUME_WINDOWS = listOf("systemui.volume", "VolumePanel", "volumedialog")
    }
}

/**
 * Whether the service takes the remote's keys. The system keeps a change past the service's
 * process, into its next connection, so the service sets it again each time it connects.
 */
internal fun AccessibilityService.filterKeys(on: Boolean) {
    val info = serviceInfo ?: return
    val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
    val flags = if (on) info.flags or flag else info.flags and flag.inv()
    if (flags == info.flags) return
    info.flags = flags
    serviceInfo = info
}
