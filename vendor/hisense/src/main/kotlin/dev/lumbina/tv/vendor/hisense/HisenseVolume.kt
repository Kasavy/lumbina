package dev.lumbina.tv.vendor.hisense

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.device.DeviceVolume
import javax.inject.Inject
import kotlin.math.absoluteValue

/**
 * Hisense's volume, through the broadcast its voice assistant and smart-home control use
 * (TvBaseService's `com.hisense.tv.aiot.VOLUME`): the same volume manager as the remote's keys.
 */
class HisenseVolume @Inject constructor(@ApplicationContext private val context: Context) : DeviceVolume {

    /** Asked once: a held volume button changes it many times, each a trip to the package manager otherwise. */
    private val hasReceiver: Boolean by lazy {
        context.packageManager.queryBroadcastReceivers(Intent(ACTION).setComponent(RECEIVER), 0).isNotEmpty()
    }

    /** False on firmware without TvBaseService's receiver: the broadcast would go nowhere, silently. */
    override fun change(percent: Int): Boolean {
        if (!hasReceiver) return false
        if (percent == 0) return true
        val intent = Intent(ACTION)
            .setComponent(RECEIVER)
            .putExtra("packageName", context.packageName)
            .putExtra("keyCode", if (percent > 0) RAISE else LOWER)
            .putExtra("keyValue", percent.absoluteValue)
        context.sendBroadcast(intent)
        return true
    }

    private companion object {
        const val ACTION = "com.hisense.tv.aiot.VOLUME"
        val RECEIVER = ComponentName("com.hisense.tv.baseservice", "com.hisense.tv.baseservice.MainReceiver")
        const val RAISE = 5012
        const val LOWER = 5013
    }
}
