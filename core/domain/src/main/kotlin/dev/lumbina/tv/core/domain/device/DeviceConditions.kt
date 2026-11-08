package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.SetupScreens
import dev.lumbina.tv.core.domain.backdrop.ScreensaverPicker
import dev.lumbina.tv.core.domain.backdrop.ScreensaverSetup
import dev.lumbina.tv.core.domain.health.SecureSettings
import javax.inject.Inject

/**
 * Everything that decides which rows, pages and ways to an access a user sees on this device, in
 * one place. What the firmware is ([DeviceProfile]) and what the device has now (screens, permissions, debugging).
 * One line of it opens a diagnostics report, so what someone's TV shows can be told from it alone.
 * Read with [ReadDeviceConditions].
 */
data class DeviceConditions(
    /** [DeviceProfile.id]: firmware's own tricks, or none. */
    val profile: String,
    /** [DeviceProfile.setupOverAdb]: the setup turns the service on over debugging, not in the settings. */
    val setupOverAdb: Boolean,
    /** Lumbina's own connection to debugging; null where the profile doesn't use one ([DeviceProfile.localAdb]). */
    val debugging: AdbState?,
    /** The system has a screen where the user turns accessibility services on. */
    val accessibilitySettings: Boolean,
    /** The system screensaver may be set by Lumbina itself (WRITE_SECURE_SETTINGS). */
    val writesSecureSettings: Boolean,
    /** Where the user can choose the screensaver by hand; null: nowhere. */
    val screensaverPicker: ScreensaverPicker?,
    /** [DeviceProfile.hasRemoteMic]: voice from the remote, the "Voice input" page. */
    val remoteMic: Boolean,
    /** [DeviceProfile.speechForApps]: Lumbina is the system's speech recognizer for other apps. */
    val speechForApps: Boolean,
    /** [DeviceProfile.feedsMicToApps]: the remote's sound may go to apps that record themselves. */
    val micToApps: Boolean,
    /** [DeviceProfile.heldVolumeStops]: the "Hold volume buttons" switch is offered. */
    val volumeHold: Boolean,
    /** [DeviceProfile.languageFirst]: the setup asks Lumbina's language first. */
    val languageFirst: Boolean,
    /** Lumbina reaches some of the TV's video inputs (a box's profile may allow them and list none). */
    val inputs: Boolean,
) {
    /** In one line, with the names the document has. */
    fun summary(): String = listOf(
        profile,
        if (setupOverAdb) "setup over adb" else "setup in settings",
        "adb ${debugging?.name ?: "unused"}",
        "a11y screen ${yesNo(accessibilitySettings)}",
        "secure settings ${yesNo(writesSecureSettings)}",
        "screensaver list ${screensaverPicker?.name ?: "none"}",
        "remote mic ${yesNo(remoteMic)}",
        "speech for apps ${yesNo(speechForApps)}",
        "mic to apps ${yesNo(micToApps)}",
        "volume hold ${yesNo(volumeHold)}",
        "language first ${yesNo(languageFirst)}",
        "inputs ${yesNo(inputs)}",
    ).joinToString(" · ")

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}

/**
 * Reads [DeviceConditions] now: the package manager, settings, the inputs' list and, unless the
 * caller has it already, a connection attempt to debugging. Off the main thread.
 */
class ReadDeviceConditions @Inject constructor(
    private val profile: DeviceProfile,
    private val access: LocalAccess,
    private val setupScreens: SetupScreens,
    private val secureSettings: SecureSettings,
    private val screensaver: ScreensaverSetup,
) {
    /** [debugging]: the connection's state where the caller polls it anyway; asked here otherwise. */
    suspend operator fun invoke(debugging: AdbState? = null): DeviceConditions = DeviceConditions(
        profile = profile.id,
        setupOverAdb = profile.setupOverAdb,
        debugging = if (profile.localAdb) debugging ?: access.state() else null,
        accessibilitySettings = setupScreens.hasAccessibilitySettings,
        writesSecureSettings = secureSettings.canWrite,
        screensaverPicker = screensaver.picker,
        remoteMic = profile.hasRemoteMic,
        speechForApps = profile.speechForApps,
        micToApps = profile.feedsMicToApps,
        volumeHold = profile.heldVolumeStops,
        languageFirst = profile.languageFirst,
        inputs = profile.inputs?.list().orEmpty().isNotEmpty(),
    )
}
