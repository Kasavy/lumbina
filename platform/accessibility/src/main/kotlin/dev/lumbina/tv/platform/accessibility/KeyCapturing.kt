package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.remote.BINDABLE_GESTURES
import dev.lumbina.tv.core.domain.remote.KeyGesture
import dev.lumbina.tv.core.domain.remote.RemoteKey

/**
 * Tells the settings which button was pressed, while they ask ("Press a button on the remote"):
 * only the service sees every key, the firmware's own included. The key is taken, so it does not
 * also do what it does; Back is left alone, it is how the settings stop asking. Main thread.
 */
internal class KeyCapturing(
    private val service: AccessibilityService,
    private val keyOf: (KeyEvent) -> RemoteKey,
    private val log: LumbinaLog,
) {
    private var asked = false

    /** Whether the settings wait for a button to be pressed. */
    val isAsking: Boolean get() = asked

    /** The key whose press was taken: its repeats and its release are taken too. */
    private var taken: Int? = null

    /** The button whose page is open in the settings: its gestures are told there, not acted on. */
    private var probed: RemoteKey? = null

    private val fromSettings = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == LumbinaActions.PROBE_KEY) {
                probed = intent.getStringExtra(LumbinaActions.EXTRA_KEY)?.let(::RemoteKey)
                log.log(TAG, "tried in the settings: ${probed?.id}")
            } else {
                asked = intent.getBooleanExtra(LumbinaActions.EXTRA_CAPTURE_ON, false)
            }
        }
    }

    fun start() {
        val filter = IntentFilter(LumbinaActions.CAPTURE_KEY).apply { addAction(LumbinaActions.PROBE_KEY) }
        ContextCompat.registerReceiver(service, fromSettings, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        service.unregisterReceiver(fromSettings)
    }

    /**
     * True if the key was taken for the settings. Only while one of Lumbina's screens is in front
     * ([inLumbina]): settings that went away without saying so must not keep the remote dead.
     */
    fun onKey(event: KeyEvent, inLumbina: () -> Boolean): Boolean {
        if (event.keyCode == taken) {
            if (event.action == KeyEvent.ACTION_UP) taken = null
            return true
        }
        val firstDown = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (!asked || event.isBack || !firstDown) return false
        if (!inLumbina()) return false.also { asked = false }
        val key = keyOf(event)
        taken = event.keyCode
        log.log(TAG, "captured for the settings: ${key.id}")
        service.sendBroadcast(
            Intent(LumbinaActions.KEY_CAPTURED)
                .setPackage(service.packageName)
                .putExtra(LumbinaActions.EXTRA_KEY, key.id),
        )
        return true
    }

    /** True if [gesture] on [key] was told to the settings and not acted on; only on Lumbina's screens, as [onKey]. */
    fun probe(key: RemoteKey, gesture: KeyGesture, inLumbina: () -> Boolean): Boolean {
        if (key != probed || gesture !in BINDABLE_GESTURES) return false
        if (!inLumbina()) return false.also { probed = null }
        service.sendBroadcast(
            Intent(LumbinaActions.KEY_GESTURE)
                .setPackage(service.packageName)
                .putExtra(LumbinaActions.EXTRA_GESTURE, gesture.name),
        )
        return true
    }

    private companion object {
        const val TAG = "a11y"
    }
}
