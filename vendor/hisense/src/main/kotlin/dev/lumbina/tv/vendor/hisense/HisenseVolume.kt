package dev.lumbina.tv.vendor.hisense

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.device.DeviceVolume
import javax.inject.Inject
import kotlin.math.absoluteValue

/**
 * Hisense's volume, through the broadcast its voice assistant and smart-home control use
 * (TvBaseService's `com.hisense.tv.aiot.VOLUME`): the same volume manager as the remote's keys.
 */
class HisenseVolume @Inject constructor(@ApplicationContext private val context: Context) : DeviceVolume {

    @Volatile
    private var receiverSeen = false

    @Volatile
    private var askedAt = 0L

    /**
     * Asked once in a while, not at every change: a held volume button changes the volume many
     * times, each a trip to the package manager otherwise, and the receiver may be turned on or
     * off while Lumbina runs.
     */
    private fun hasReceiver(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (askedAt == 0L || now - askedAt >= ASK_AGAIN_MILLIS) {
            receiverSeen =
                context.packageManager.queryBroadcastReceivers(Intent(ACTION).setComponent(RECEIVER), 0).isNotEmpty()
            askedAt = now
        }
        return receiverSeen
    }

    /** False on firmware without TvBaseService's receiver: the broadcast would go nowhere, silently. */
    override fun change(percent: Int): Boolean {
        if (!hasReceiver()) return false
        if (percent == 0) return true
        val intent = Intent(ACTION)
            .setComponent(RECEIVER)
            .putExtra("packageName", context.packageName)
            .putExtra("keyCode", if (percent > 0) RAISE else LOWER)
            .putExtra("keyValue", percent.absoluteValue)
        context.sendBroadcast(intent)
        return true
    }

    /** As the remote's mute key; false where nothing takes it. */
    override fun toggleMute(): Boolean {
        val intent = Intent(SYSTEM_KEY).putExtra(Intent.EXTRA_KEY_EVENT, KEYCODE_MUTE)
        if (context.packageManager.queryBroadcastReceivers(intent, 0).isEmpty()) return false
        context.sendOrderedBroadcast(intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND), null)
        return true
    }

    internal companion object {
        const val PACKAGE = "com.hisense.tv.baseservice"
        private const val ACTION = "com.hisense.tv.aiot.VOLUME"
        private val RECEIVER = ComponentName(PACKAGE, "$PACKAGE.MainReceiver")
        private const val RAISE = 5012
        private const val LOWER = 5013
        private const val ASK_AGAIN_MILLIS = 5_000L
        private const val SYSTEM_KEY = "tv.policy.SYSTEM_KEY"
        private const val KEYCODE_MUTE = 164
    }
}
