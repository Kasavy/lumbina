package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.remote.KeyMap
import dev.lumbina.tv.core.domain.speech.AudioSource

/** What Lumbina can observe about the device it runs on. */
interface DeviceFingerprint {
    val manufacturer: String
    val model: String
    fun isInstalled(packageName: String): Boolean
}

/**
 * Device-family specific behaviour. Each vendor module contributes one; the best match
 * (highest [priority] among those that [matches]) is used. A generic profile matches everything.
 */
interface DeviceProfile {
    val id: String
    val priority: Int
    val keyMap: KeyMap

    /** The firmware's own launcher; null if none. */
    val stockLauncher: AppId?

    /**
     * Whether a window ([className] of [packageName]; null when the class is not known) is the
     * firmware's home screen, which may come up at power-on instead of Lumbina: over it Lumbina
     * opens again. Any window of [stockLauncher] unless the profile knows better.
     */
    fun isHomeScreen(packageName: String, className: String?): Boolean = packageName == stockLauncher?.packageName

    /** Audio from the remote's mic, if the vendor exposes it; null keeps the mic key with the firmware. */
    val remoteMicAudio: AudioSource? get() = null

    /** Whether Lumbina can hear the remote, so its voice input can work at all. */
    val hasRemoteMic: Boolean get() = remoteMicAudio != null

    /**
     * Whether Lumbina should be the system's speech recognizer for other apps: where the firmware
     * has none that hears the remote. Elsewhere the system's own stays.
     */
    val speechForApps: Boolean get() = false

    /**
     * Android's microphone input hears nothing on this device, so apps that record their voice
     * search themselves get silence. Where true, Lumbina feeds the remote's audio into the
     * recordings of the apps the user allows (`AccessSettings.micApps`), and of an app that
     * recognizes speech for Lumbina itself, see [feedsMicToApps].
     */
    val micInjection: Boolean get() = false

    /** Whether Lumbina can do that here: the feeding is done over its own adb. */
    val feedsMicToApps: Boolean get() = micInjection && localAdb

    /** The firmware's own volume where Android's doesn't reach the speakers; null: Android's. */
    val volume: DeviceVolume? get() = null

    /**
     * Whether a held volume button changes the volume by one step only while Lumbina's service
     * takes the buttons (Android's volume on a TV: Mi Box), so the service has to let them go.
     * Vidda, whose firmware has its own volume, repeats a held button as without the service.
     */
    val heldVolumeStops: Boolean get() = volume == null

    /** The video inputs an app can choose between; null where there are none, or none Lumbina can reach. */
    val inputs: InputSources? get() = null

    /**
     * Debugging (adb) listens on the local network without TLS pairing once the user turns it
     * on, so Lumbina can connect to it itself (see `LocalAdb`).
     */
    val localAdb: Boolean get() = false

    /**
     * The parts of the firmware this profile's features rest on, see [FirmwareParts]. Each must be
     * visible to Lumbina (a `queries` entry in the manifest), or it reads as missing.
     */
    val firmwareParts: List<FirmwarePart> get() = emptyList()

    /** What a diagnostics report asks about this firmware; null: nothing beyond the common facts. */
    val diagnostics: DeviceDiagnostics? get() = null

    /**
     * The firmware was made for another market, so the device's language says nothing about who
     * uses it. There the setup asks Lumbina's language first.
     */
    val languageFirst: Boolean get() = false

    fun matches(device: DeviceFingerprint): Boolean
}

/** A question put to the device for a diagnostics report, with what it answered. */
data class DeviceAnswer(val question: String, val answer: String)

/** What a diagnostics report finds out about one firmware, so a tester need not be asked to. */
interface DeviceDiagnostics {
    /** Shell commands whose output the report carries, run over Lumbina's own adb (`DeviceProfile.localAdb`). */
    val shellCommands: List<String> get() = emptyList()

    /** What the firmware's own services answer when asked directly. Blocks while it asks. */
    fun probe(): List<DeviceAnswer> = emptyList()
}

/** Picks the active [DeviceProfile]. */
class DeviceProfileResolver(private val profiles: Set<DeviceProfile>) {

    fun resolve(device: DeviceFingerprint): DeviceProfile =
        profiles.filter { it.matches(device) }.maxByOrNull { it.priority }
            ?: error("No device profile matches; the generic profile must always match")
}
