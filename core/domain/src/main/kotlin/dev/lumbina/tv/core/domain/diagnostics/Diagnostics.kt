package dev.lumbina.tv.core.domain.diagnostics

import dev.lumbina.tv.core.domain.device.DeviceAnswer
import dev.lumbina.tv.core.domain.device.FirmwarePart
import dev.lumbina.tv.core.domain.device.PackageState

/** The device and app facts a support report needs; read live, see [snapshot]. */
interface SystemInfo {
    val appVersion: String
    val debugBuild: Boolean
    val manufacturer: String
    val model: String
    val androidRelease: String
    val sdkInt: Int
    val totalRamBytes: Long
    val availableRamBytes: Long
    val freeStorageBytes: Long

    /** The firmware build as it names itself; models of one family may share [model]. */
    val firmware: String
    val fingerprint: String

    /** Board and chip, as far as the system tells. */
    val hardware: String
    val uptimeMillis: Long

    /** Remotes, keyboards and the like, each as "name (vendor:product)". */
    val inputDevices: List<String>

    /** The package that answers the Home intent; null when the system has not settled on one. */
    val homeApp: String?
    val microphoneGranted: Boolean

    /** Lumbina's own processes alive now. */
    val processes: List<String>

    /** Why Lumbina's processes ended lately, newest first; empty where the system doesn't tell. */
    val processExits: List<String>

    /** The version of an installed package Lumbina can see; null when it isn't there. */
    fun packageVersion(packageName: String): String?
}

/** [SystemInfo]'s values at one moment. */
data class SystemFacts(
    val appVersion: String,
    val debugBuild: Boolean,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val firmware: String,
    val fingerprint: String,
    val hardware: String,
    val uptimeMillis: Long,
    val inputDevices: List<String>,
    val homeApp: String?,
    val microphoneGranted: Boolean,
    val processes: List<String>,
    val processExits: List<String>,
)

/** Reads every value once, so a report or screen shows one consistent moment. */
fun SystemInfo.snapshot(): SystemFacts = SystemFacts(
    appVersion = appVersion,
    debugBuild = debugBuild,
    manufacturer = manufacturer,
    model = model,
    androidRelease = androidRelease,
    sdkInt = sdkInt,
    totalRamBytes = totalRamBytes,
    availableRamBytes = availableRamBytes,
    freeStorageBytes = freeStorageBytes,
    firmware = firmware,
    fingerprint = fingerprint,
    hardware = hardware,
    uptimeMillis = uptimeMillis,
    inputDevices = inputDevices,
    homeApp = homeApp,
    microphoneGranted = microphoneGranted,
    processes = processes,
    processExits = processExits,
)

/** A package of the firmware as a report tells it: its version if it has one, and its state when not plainly on. */
data class FirmwarePackage(val name: String, val version: String?, val state: PackageState) {
    fun said(): String = when (state) {
        PackageState.Missing -> "NOT installed"
        PackageState.Removed -> "REMOVED for this user"
        PackageState.Disabled -> "${version ?: "installed"}, DISABLED"
        PackageState.Enabled -> version ?: "installed"
    }
}

/** Lumbina's own log, newest lines last. */
fun interface LogArchive {
    fun tail(lines: Int): List<String>
}

/** Saves a report somewhere the user can reach (e.g. the Download folder). */
fun interface ReportExporter {
    /** Returns where the report went, for showing to the user; null if it could not be saved. */
    fun save(fileName: String, text: String): String?
}

/** The accesses Lumbina has, and whether it is set to open on power-on. */
data class AccessFacts(
    /** The state of Lumbina's own adb connection, in a word; null where the profile doesn't use one. */
    val debugging: String?,
    val openOnPowerOn: Boolean,
    val notifications: Boolean,
    val usage: Boolean,
)

/** Where channels and "continue watching" come from, and how much came. */
data class ChannelsFacts(
    val provider: String?,
    /** The provider keeps other apps' rows behind a permission the user has not granted. */
    val permissionMissing: Boolean,
    val channels: Int,
    val programs: Int,
    val watchNext: Int,
)

/** One look at Lumbina's state, taken when the diagnostics screen asks for it. */
data class DiagnosticsSnapshot(
    val system: SystemFacts,
    val deviceProfile: String,
    val stockLauncher: String?,
    val serviceRunning: Boolean,
    val canHealService: Boolean,
    val remoteMic: Boolean,
    val voiceLanguages: List<String>,
    val installedModels: List<String>,
    val voiceMode: String,
    val lumbinaKeyboard: Boolean,
    val installedApps: Int,
    val favorites: Int,
    val categories: Int,
    val hiddenApps: Int,
    val access: AccessFacts,
    val channels: ChannelsFacts,
    /** The packages of the firmware Lumbina leans on. */
    val firmware: List<FirmwarePackage>,
    /** Those of them a feature rests on that are turned off, see `FirmwareParts`. */
    val partsOff: List<FirmwarePart>,
) {
    /**
     * Plain-text report (English, for whoever helps), followed by what the device [answered] when
     * asked and the recent [log].
     */
    fun toReport(takenAt: String, log: List<String>, answered: List<DeviceAnswer> = emptyList()): String = buildString {
        appendLine("Lumbina diagnostics — $takenAt")
        section("App") {
            line("Version", system.appVersion + if (system.debugBuild) " (debug)" else "")
            line("Device profile", deviceProfile)
            line("Stock launcher", stockLauncher ?: "none")
        }
        device()
        remote()
        content()
        if (answered.isNotEmpty()) {
            section("Asked of the device") {
                answered.forEach { (question, answer) ->
                    appendLine("> $question")
                    appendLine(answer.trim().ifEmpty { "(nothing)" })
                }
            }
        }
        appendLine()
        appendLine("Recent log:")
        log.forEach(::appendLine)
    }

    private fun StringBuilder.device() {
        section("Device") {
            line("Model", "${system.manufacturer} ${system.model}")
            line("Android", "${system.androidRelease} (API ${system.sdkInt})")
            line("Firmware", system.firmware)
            line("Fingerprint", system.fingerprint)
            line("Hardware", system.hardware)
            line("Up", "${system.uptimeMillis / MILLIS_PER_MINUTE} min")
            line("RAM", "${mib(system.availableRamBytes)} free of ${mib(system.totalRamBytes)}")
            line("Storage", "${mib(system.freeStorageBytes)} free")
            system.inputDevices.forEach { line("Input device", it) }
            firmware.forEach { line(it.name, it.said()) }
        }
        section("Processes") {
            line("Running", system.processes.joinToString().ifEmpty { "unknown" })
            system.processExits.forEach { line("Ended", it) }
        }
    }

    private fun StringBuilder.remote() {
        section("Remote") {
            line("Accessibility service", if (serviceRunning) "running" else "NOT running")
            line("Self-healing grant", if (canHealService) "granted" else "not granted")
            line("Remote mic", if (remoteMic) "supported" else "not supported")
            line("Microphone permission", granted(system.microphoneGranted))
            line("Home app", system.homeApp ?: "none")
            line("Open on power-on", if (access.openOnPowerOn) "Lumbina" else "nothing")
        }
        section("Access") {
            line("Debugging connection", access.debugging ?: "not used on this device")
            line("Notification access", granted(access.notifications))
            line("Usage access", granted(access.usage))
        }
        section("Voice") {
            line("Enabled languages", voiceLanguages.joinToString().ifEmpty { "none" })
            line("Installed models", installedModels.joinToString().ifEmpty { "none" })
            line("Mode", voiceMode)
            line("Keyboard", if (lumbinaKeyboard) "Lumbina" else "system")
        }
    }

    private fun StringBuilder.content() {
        section("Channels") {
            line("TV provider", channels.provider ?: "none")
            line("Listings permission", if (channels.permissionMissing) "NOT granted" else "granted or not needed")
            line("Channels", "${channels.channels} (${channels.programs} programs)")
            line("Continue watching", channels.watchNext.toString())
        }
        section("Home") {
            line("Installed apps", installedApps.toString())
            line("Favorites", favorites.toString())
            line("Categories", categories.toString())
            line("Hidden apps", hiddenApps.toString())
        }
    }

    private inline fun StringBuilder.section(title: String, lines: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("[$title]")
        lines()
    }

    private fun StringBuilder.line(label: String, value: String) {
        appendLine("$label: $value")
    }

    private fun mib(bytes: Long): String = "${bytes / BYTES_PER_MIB} MB"

    private fun granted(yes: Boolean): String = if (yes) "granted" else "NOT granted"

    private companion object {
        const val BYTES_PER_MIB = 1024 * 1024
        const val MILLIS_PER_MINUTE = 60_000
    }
}
