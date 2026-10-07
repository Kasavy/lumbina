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

    /** The vendor's own launcher, reachable with a long press on Home; null if none. */
    val stockLauncher: AppId?

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
     * recordings of the apps the user allows (`AccessSettings.micApps`), see [feedsMicToApps].
     */
    val micInjection: Boolean get() = false

    /** Whether Lumbina can do that here: the feeding is done over its own adb. */
    val feedsMicToApps: Boolean get() = micInjection && localAdb

    /** The firmware's own volume where Android's doesn't reach the speakers; null: Android's. */
    val volume: DeviceVolume? get() = null

    /**
     * Debugging (adb) listens on the local network without TLS pairing once the user turns it
     * on, so Lumbina can connect to it itself (see `LocalAdb`).
     */
    val localAdb: Boolean get() = false

    /** What a diagnostics report asks about this firmware; null: nothing beyond the common facts. */
    val diagnostics: DeviceDiagnostics? get() = null

    fun matches(device: DeviceFingerprint): Boolean
}

/** A question put to the device for a diagnostics report, with what it answered. */
data class DeviceAnswer(val question: String, val answer: String)

/** What a diagnostics report finds out about one firmware, so a tester need not be asked to. */
interface DeviceDiagnostics {
    /**
     * Packages of the firmware the profile leans on: the report says which are there, and their
     * versions. Each must be visible to Lumbina (a `queries` entry in the manifest), or it reads
     * as missing.
     */
    val packages: List<String> get() = emptyList()

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
