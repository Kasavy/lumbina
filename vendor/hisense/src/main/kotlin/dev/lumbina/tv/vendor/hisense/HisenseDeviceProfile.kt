package dev.lumbina.tv.vendor.hisense

import android.os.Build
import android.view.KeyEvent
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.DeviceDiagnostics
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.device.DeviceVolume
import dev.lumbina.tv.core.domain.device.FirmwarePart
import dev.lumbina.tv.core.domain.device.InputSources
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
class HisenseDeviceProfile @Inject constructor(hisenseVolume: HisenseVolume, hisenseInputs: HisenseInputs) :
    DeviceProfile {
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

    /**
     * The start page may also be the "simple" one: the same activity in the voice assistant's
     * package or in the widgets', whose other windows (the assistant itself) are not a home screen.
     */
    override fun isHomeScreen(packageName: String, className: String?): Boolean = when (packageName) {
        STOCK_LAUNCHER_PACKAGE -> true
        SIMPLE_HOME_PACKAGE -> className == null || className == SIMPLE_HOME_ACTIVITY
        SIMPLE_HOME_WIDGETS_PACKAGE -> className == SIMPLE_HOME_ACTIVITY
        else -> false
    }

    /** The remote's mic is not an Android audio input; HsPcmShare serves it decoded. */
    override val remoteMicAudio: AudioSource = PcmShareAudioSource(MicInUse::hold) { LocalSocketTransport.connect() }

    /** The firmware's own recognizer serves only its own apps. */
    override val speechForApps = true

    /**
     * Android's microphone input has no sound here. An app's recordings can be fed only since
     * Android 13, where an audio mix can be aimed at one app's recordings and the adb shell may
     * register the policy for it.
     */
    override val micInjection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The music stream stays at full; the firmware sets the level in its sound processor. */
    override val volume: DeviceVolume = hisenseVolume

    override val inputs: InputSources = hisenseInputs

    /** "USB debugging" also opens adb on port 5555 of the network. */
    override val localAdb = true

    override val languageFirst = true

    override val firmwareParts = listOf(
        FirmwarePart(PcmShareProtocol.PACKAGE, FirmwarePart.Feature.RemoteMic),
        // Some models give no sound from the remote unless this one is on as well.
        FirmwarePart(MIC_SERVICE_PACKAGE, FirmwarePart.Feature.RemoteMic),
        FirmwarePart(HisenseVolume.PACKAGE, FirmwarePart.Feature.Volume),
    )

    override val diagnostics: DeviceDiagnostics = HisenseDiagnostics()

    override fun matches(device: DeviceFingerprint): Boolean = device.isInstalled(STOCK_LAUNCHER_PACKAGE)

    private companion object {
        const val STOCK_LAUNCHER_PACKAGE = "com.jamdeo.tv.vod"
        const val MIC_SERVICE_PACKAGE = "com.vt.mic.service"
        const val SIMPLE_HOME_PACKAGE = "com.keylab.speech.view.vidaa"
        const val SIMPLE_HOME_WIDGETS_PACKAGE = "com.hisense.widget.pro"
        const val SIMPLE_HOME_ACTIVITY = "com.ju.secondscreen.HomeActivity"
        const val SCAN_HOME = 102
        const val SCAN_MIC = 166
        const val KEYCODE_HISENSE_VOICE = 4381 // HISENSE_VOICE_NANOSIC
    }
}
