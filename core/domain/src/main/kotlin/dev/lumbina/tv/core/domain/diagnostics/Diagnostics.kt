package dev.lumbina.tv.core.domain.diagnostics

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
)

/** Lumbina's own log, newest lines last. */
fun interface LogArchive {
    fun tail(lines: Int): List<String>
}

/** Saves a report somewhere the user can reach (e.g. the Download folder). */
fun interface ReportExporter {
    /** Returns where the report went, for showing to the user; null if it could not be saved. */
    fun save(fileName: String, text: String): String?
}

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
) {
    /** Plain-text report (English, for whoever helps), followed by the recent [log]. */
    fun toReport(takenAt: String, log: List<String>): String = buildString {
        appendLine("Lumbina diagnostics — $takenAt")
        section("App") {
            line("Version", system.appVersion + if (system.debugBuild) " (debug)" else "")
            line("Device profile", deviceProfile)
            line("Stock launcher", stockLauncher ?: "none")
        }
        section("Device") {
            line("Model", "${system.manufacturer} ${system.model}")
            line("Android", "${system.androidRelease} (API ${system.sdkInt})")
            line("RAM", "${mib(system.availableRamBytes)} free of ${mib(system.totalRamBytes)}")
            line("Storage", "${mib(system.freeStorageBytes)} free")
        }
        section("Remote") {
            line("Accessibility service", if (serviceRunning) "running" else "NOT running")
            line("Self-healing grant", if (canHealService) "granted" else "not granted")
            line("Remote mic", if (remoteMic) "supported" else "not supported")
        }
        section("Voice") {
            line("Enabled languages", voiceLanguages.joinToString().ifEmpty { "none" })
            line("Installed models", installedModels.joinToString().ifEmpty { "none" })
            line("Mode", voiceMode)
            line("Keyboard", if (lumbinaKeyboard) "Lumbina" else "system")
        }
        section("Home") {
            line("Installed apps", installedApps.toString())
            line("Favorites", favorites.toString())
            line("Categories", categories.toString())
            line("Hidden apps", hiddenApps.toString())
        }
        appendLine()
        appendLine("Recent log:")
        log.forEach(::appendLine)
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

    private companion object {
        const val BYTES_PER_MIB = 1024 * 1024
    }
}
