package dev.lumbina.tv.core.domain.access

import dev.lumbina.tv.core.domain.health.AccessibilityServiceList
import dev.lumbina.tv.core.domain.health.HealAccessibilityService
import dev.lumbina.tv.core.domain.health.SecureSettings
import dev.lumbina.tv.core.domain.settings.SettingsRepository

/** How far Lumbina's own connection to the device's debugging gets. */
enum class AdbState {
    /** Debugging is off (nothing listens), or the device refused the connection. */
    Off,

    /** Debugging is on but Lumbina's key isn't allowed yet: [LocalAdb.connect] asks the user. */
    NotAllowed,

    Connected,

    /** Debugging answered, but connecting failed on Lumbina's side (its key, the signature); the log says why. */
    Failed,
}

/** What "Connect" achieved, from the connection to Lumbina being set up. */
enum class SetupResult {
    /** Connected, and Lumbina may keep its service on: the service is coming on. */
    Done,

    /** Connected, but the device refused the permission to keep the service on. */
    NotKept,
    NotAllowed,
    Off,
    Failed,
}

/**
 * Lumbina's connection to this device's adb over the local network: a normal app may not grant
 * itself permissions or stop other apps, a shell may. Used only where the device profile says
 * debugging works this way (see `DeviceProfile.localAdb`).
 */
interface LocalAdb {
    /** Whether debugging is on and Lumbina's key allowed; never shows the device's prompt. */
    suspend fun state(): AdbState

    /** Connects, showing the device's "Allow debugging?" prompt for Lumbina's key if needed. */
    suspend fun connect(): AdbState

    /** Runs [command] in a shell and returns its output; null when not connected. */
    suspend fun shell(command: String): String?

    /**
     * Runs [command] with [input] as its standard input and returns its output; null when not
     * connected. For what the shell may not open itself, like a file in an app's folder. The
     * command must know how much to read: the input is sent whole and then left open.
     */
    suspend fun exec(command: String, input: ByteArray): String? = null
}

/** What Lumbina does with that access; shared by the settings screen and the remote's process. */
data class AccessSettings(
    /** Holding OK on an app in the switcher stops it. */
    val closeFromRecents: Boolean = true,
    /** Open Lumbina when the device turns on. */
    val openOnPowerOn: Boolean = false,
    /** "Hide" on the home screen's setup card; the steps stay in the settings. */
    val setupCardHidden: Boolean = false,
    /**
     * The user's answers about apps that record the microphone themselves: true lets the app
     * hear the remote, false keeps it from it. An app not here has not been asked about.
     */
    val micApps: Map<String, Boolean> = emptyMap(),
)

typealias AccessSettingsRepository = SettingsRepository<AccessSettings>

/**
 * The things Lumbina does over [LocalAdb]. Keeping the remote's service on is the system's
 * WRITE_SECURE_SETTINGS permission: with it, [heal] turns the service back on whenever the
 * firmware drops it.
 */
class LocalAccess(
    private val adb: LocalAdb,
    private val secureSettings: SecureSettings,
    private val heal: HealAccessibilityService,
    private val packageName: String,
    /** The class of Lumbina's notification listener. */
    private val notificationListener: String,
    /** Apps Lumbina carries for devices that lack a part of the system. */
    private val companions: List<CompanionApp> = emptyList(),
) {
    suspend fun state(): AdbState = adb.state()

    /**
     * Connects (asking the user if needed) and sets Lumbina up: the service kept on, usage
     * statistics, notifications, and the companion apps the device misses.
     */
    suspend fun connectAndSetUp(): SetupResult = when (adb.connect()) {
        AdbState.Connected -> setUp()
        AdbState.NotAllowed -> SetupResult.NotAllowed
        AdbState.Off -> SetupResult.Off
        AdbState.Failed -> SetupResult.Failed
    }

    private suspend fun setUp(): SetupResult {
        val kept = grantSecureSettings(true)
        renew()
        if (!kept) return SetupResult.NotKept
        heal()
        return SetupResult.Done
    }

    /**
     * What a connected Lumbina sets up besides keeping its service on, done again: a version that
     * learnt to set up something new does it for those who connected before. Never asks the user;
     * false when not connected.
     */
    suspend fun renew(): Boolean {
        if (adb.state() != AdbState.Connected) return false
        grantUsageStats()
        // Notifications for the bell and "now playing": firmware that needs this connection has
        // no screen where the user could allow Lumbina's listener.
        adb.shell("cmd notification allow_listener $packageName/$notificationListener")
        companions.filter { it.missing() }.forEach { it.install(adb) }
        return true
    }

    val keepsService: Boolean get() = secureSettings.canWrite

    /** Gives or takes back the permission, and with it brings the service on. False when not connected or refused. */
    suspend fun setKeepService(on: Boolean): Boolean {
        if (!grantSecureSettings(on)) return false
        if (on) heal()
        return true
    }

    /** `pm` prints its error rather than failing the shell, so the permission itself tells whether it took. */
    private suspend fun grantSecureSettings(on: Boolean): Boolean {
        val verb = if (on) "grant" else "revoke"
        return adb.shell("pm $verb $packageName android.permission.WRITE_SECURE_SETTINGS") != null &&
            secureSettings.canWrite == on
    }

    suspend fun grantUsageStats() {
        adb.shell("appops set $packageName GET_USAGE_STATS allow")
    }

    /**
     * Asks for the microphone permission a speech recognizer must hold: the system checks the
     * recognizer's own too (Lumbina reads the remote, never the microphone). `pm` reports a
     * refusal as text, not as a failure: the caller sees whether it took from the permission itself.
     */
    suspend fun grantRecordAudio() {
        adb.shell("pm grant $packageName android.permission.RECORD_AUDIO")
    }

    /** Makes [service] (a flattened component name) the system's speech recognizer; false without access. */
    suspend fun setSpeechRecognizer(service: String): Boolean =
        adb.shell("settings put secure voice_recognition_service $service") != null

    /**
     * Starts a program from [apk] (its [mainClass], with `app_process`) as the shell, in a session
     * of its own so it outlives the connection, its output dropped; false without access. [args]
     * are plain words. The new session is made in the foreground and the program goes to the
     * background inside it: backgrounding `setsid` itself raced with the connection closing, and
     * the program was sometimes killed at birth.
     */
    suspend fun startShellProgram(apk: String, mainClass: String, args: List<String>): Boolean {
        val program = "env CLASSPATH='$apk' app_process /system/bin $mainClass ${args.joinToString(" ")}"
        return adb.shell("setsid sh -c \"$program </dev/null >/dev/null 2>&1 &\"") != null
    }

    /** Stops [app] as if from the system's app info; false without access. */
    suspend fun forceStop(app: String): Boolean = app != packageName && adb.shell("am force-stop $app") != null
}

/** The system's settings screens where the user finishes the setup; which exist depends on the device. */
interface SetupScreens {
    /** Where the user turns accessibility services on (missing on some firmware). */
    val hasAccessibilitySettings: Boolean

    fun openAccessibilitySettings(): Boolean

    fun openDeveloperSettings(): Boolean
}

/** What to run on a computer (adb) where Lumbina can't turn its service on by itself. */
object ComputerSetup {
    fun commands(address: String?, enabledServices: String?, ourService: String, packageName: String): List<String> {
        val services = AccessibilityServiceList.withoutOurs(enabledServices, ourService) + ourService
        return listOf(
            "adb connect ${address ?: "<IP>"}",
            "adb shell settings put secure enabled_accessibility_services ${AccessibilityServiceList.join(services)}",
            "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
        )
    }
}
