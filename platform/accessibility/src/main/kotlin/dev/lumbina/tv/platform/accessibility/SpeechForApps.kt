package dev.lumbina.tv.platform.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Lumbina's speech recognizer for other apps ([RecognizeSpeechActivity], [LumbinaRecognitionService])
 * exists only where the device's profile asks for it (`DeviceProfile.speechForApps`). Elsewhere it is
 * off: next to the system's recognizer it would only add a "which app?" prompt, and fail.
 */
object SpeechForApps {
    private val components = listOf(RecognizeSpeechActivity::class.java, LumbinaRecognitionService::class.java)

    /** Call on every process start: a cheap check, it writes only when the state differs. */
    fun sync(context: Context, wanted: Boolean) {
        val pm = context.packageManager
        val state = if (wanted) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        for (component in components.map { ComponentName(context, it) }) {
            if (pm.getComponentEnabledSetting(component) != state) {
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
