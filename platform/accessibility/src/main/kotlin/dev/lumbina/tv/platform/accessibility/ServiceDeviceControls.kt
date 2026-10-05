package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import dev.lumbina.tv.core.domain.device.DeviceVolume
import dev.lumbina.tv.core.domain.remote.LumbinaHome
import dev.lumbina.tv.core.domain.voice.DeviceControls
import dev.lumbina.tv.core.domain.voice.MediaAction
import dev.lumbina.tv.core.domain.voice.VolumeChange

/**
 * Voice and phone commands on Android: the music volume (through the [deviceVolume] where the
 * device has its own, with the system volume bar), media keys to whatever holds the active media
 * session, Back and standby as global actions, and Home as Lumbina (the firmware's own home is a
 * different launcher). Apps are force-stopped where Lumbina may ([stop], local adb); the [launchers]
 * are neither reported in front nor stopped.
 */
internal class ServiceDeviceControls(
    private val service: AccessibilityService,
    private val lumbinaHome: LumbinaHome,
    private val deviceVolume: DeviceVolume?,
    private val launchers: Set<String>,
    private val stop: (suspend (String) -> Boolean)?,
) : DeviceControls {

    private val audio = service.getSystemService(AudioManager::class.java)

    override fun volume(change: VolumeChange): Boolean {
        when (change) {
            VolumeChange.Up -> stepVolume(1)
            VolumeChange.Down -> stepVolume(-1)
            VolumeChange.Mute -> adjustVolume(AudioManager.ADJUST_MUTE)
            VolumeChange.Unmute -> adjustVolume(AudioManager.ADJUST_UNMUTE)
        }
        return true
    }

    override fun media(action: MediaAction): Boolean {
        val code = when (action) {
            MediaAction.Play -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.Pause -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0))
        return true
    }

    override fun home(): Boolean {
        lumbinaHome.open(toStart = false)
        return true
    }

    override fun back(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    override fun standby(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

    override fun appInFront(): String? =
        service.rootInActiveWindow?.packageName?.toString()?.takeUnless { it in launchers }

    override suspend fun forceStop(packageName: String): Boolean =
        packageName !in launchers && stop?.invoke(packageName) == true

    /**
     * One step up ([direction] > 0) or down. People often listen low, so a step is a few percent of
     * the range: a single volume step may not be heard, a tenth is too much. Raising unmutes first.
     */
    private fun stepVolume(direction: Int) {
        if (direction > 0 && audio.isStreamMute(STREAM)) adjustVolume(AudioManager.ADJUST_UNMUTE)
        if (deviceVolume?.change(direction * PERCENT / STEPS) == true) return
        val max = audio.getStreamMaxVolume(STREAM)
        val step = maxOf(1, max / STEPS)
        val level = (audio.getStreamVolume(STREAM) + direction * step).coerceIn(0, max)
        audio.setStreamVolume(STREAM, level, AudioManager.FLAG_SHOW_UI)
    }

    private fun adjustVolume(direction: Int) = audio.adjustStreamVolume(STREAM, direction, AudioManager.FLAG_SHOW_UI)

    private companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
        const val STEPS = 25
        const val PERCENT = 100
    }
}
