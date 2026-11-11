package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.lumbina.tv.core.common.LumbinaActions

/**
 * Tells the service's sleep watcher a pointer is in use (a mouse on Lumbina's screens, the phone
 * remote's page), at most once per [EVERY_MILLIS]: it counts only the remote's keys as activity
 * otherwise, and the screensaver or auto-sleep would come under a moving pointer. Any thread.
 */
class PointerUse(private val context: Context) {
    @Volatile private var toldAt = Long.MIN_VALUE / 2

    fun used() {
        val now = SystemClock.uptimeMillis()
        if (now - toldAt < EVERY_MILLIS) return
        toldAt = now
        context.sendBroadcast(Intent(LumbinaActions.POINTER_USED).setPackage(context.packageName))
    }

    private companion object {
        /** The idle clock counts minutes. */
        const val EVERY_MILLIS = 10_000L
    }
}
