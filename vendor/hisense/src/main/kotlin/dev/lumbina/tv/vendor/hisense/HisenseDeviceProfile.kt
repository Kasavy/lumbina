package dev.lumbina.tv.vendor.hisense

import android.view.KeyEvent
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.device.DeviceVolume
import dev.lumbina.tv.core.domain.remote.KeyMap
import dev.lumbina.tv.core.domain.remote.RemoteKey
import dev.lumbina.tv.core.domain.speech.AudioSource
import javax.inject.Inject

/**
 * Hisense VIDAA devices on Chinese firmware (e.g. Vidda laser projectors).
 *
 * The Bluetooth remote's key layout (Vendor_000d_Product_3838.kl) maps Home (scan code 102)
 * to KEYCODE_F12, and the firmware opens its own launcher on F12, ignoring the HOME role.
 * The mic button is scan code 166 → vendor keycode 4381. Keycodes are matched too, for key
 * events without a scan code (keys injected with `adb shell input keyevent` never reach the
 * accessibility service, though).
 */
class HisenseDeviceProfile @Inject constructor(hisenseVolume: HisenseVolume) : DeviceProfile {
    override val id = "hisense-vidaa"
    override val priority = 100

    override val keyMap = KeyMap { scanCode, keyCode ->
        when {
            scanCode == SCAN_HOME || keyCode == KeyEvent.KEYCODE_F12 -> RemoteKey.Home
            scanCode == SCAN_MIC || keyCode == KEYCODE_HISENSE_VOICE -> RemoteKey.Mic
            else -> null
        }
    }

    override val stockLauncher = AppId(STOCK_LAUNCHER_PACKAGE, "com.ju.main.launcher.LauncherActivity")

    /** The remote's mic is not an Android audio input; HsPcmShare serves it decoded. */
    override val remoteMicAudio: AudioSource = PcmShareAudioSource { LocalSocketTransport.connect() }

    /** The firmware's own recognizer serves only its own apps. */
    override val speechForApps = true

    /** Android's microphone input has no sound here. */
    override val micInjection = true

    /** The music stream stays at full; the firmware sets the level in its sound processor. */
    override val volume: DeviceVolume = hisenseVolume

    /** "USB debugging" also opens adb on port 5555 of the network. */
    override val localAdb = true

    override fun matches(device: DeviceFingerprint): Boolean = device.isInstalled(STOCK_LAUNCHER_PACKAGE)

    private companion object {
        const val STOCK_LAUNCHER_PACKAGE = "com.jamdeo.tv.vod"
        const val SCAN_HOME = 102
        const val SCAN_MIC = 166
        const val KEYCODE_HISENSE_VOICE = 4381 // HISENSE_VOICE_NANOSIC
    }
}
