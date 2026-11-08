package dev.lumbina.tv.core.domain.access

import dev.lumbina.tv.core.domain.health.AccessibilityServiceList
import dev.lumbina.tv.core.domain.health.HealResult
import dev.lumbina.tv.core.domain.health.SecureSettings
import dev.lumbina.tv.core.domain.settings.SettingsRepository
import java.io.InputStream

/** How far Lumbina's own connection to the device's debugging gets. */
enum class AdbState {
    /** Debugging is off: nothing listens, or what answered went away. */
    Off,

    /**
     * Debugging is on, but nothing listens on the port: the device opens it for a computer on a
     * cable only, or only through Android 11's pairing with a code. Lumbina can't connect here.
     */
    NotListening,

    /** Debugging is on but Lumbina's key isn't allowed yet: [LocalAdb.connect] asks the user. */
    NotAllowed,

    Connected,

    /**
     * Debugging is on and answered, but the connection failed: Lumbina's key or signature, or the
     * device timed out or closed it. The log says why.
     */
    Failed,
}

/** What "Connect" achieved, from the connection to Lumbina being set up. */
enum class SetupResult {
    /** Connected, and Lumbina may keep its service on: the service is coming on. */
    Done,

    /** Connected, but the device refused the permission to keep the service on. */
    NotKept,

    /**
     * Allowed for that one connection only ("Always allow" left unticked): the next connection,
     * which grants the permissions, was refused.
     */
    AllowedOnce,
    NotAllowed,
    Off,

    /** See [AdbState.NotListening]. */
    NotListening,
    Failed,
}

/**
 * Lumbina's connection to this device's adb over the local network: a normal app may not grant
 * itself permissions or stop other apps, a shell may. Used where the device profile allows it
 * (`DeviceProfile.localAdb`); whether the device lets Lumbina in is found out by connecting.
 */
interface LocalAdb {
    /** Whether debugging is on and Lumbina's key allowed; never shows the device's prompt. */
    suspend fun state(): AdbState

    /** Connects, showing the device's "Allow debugging?" prompt for Lumbina's key if needed. */
    suspend fun connect(): AdbState

    /** Runs [command] in a shell and returns its output; null when not connected. */
    suspend fun shell(command: String): String?

    /** As [shell], logged only when it fails: for what runs again and again and is known to work. */
    suspend fun shellQuietly(command: String): String? = shell(command)

    /**
     * Runs [command] with [input] as its standard input and returns its output; null when not
     * connected. For what the shell may not open itself, like a file in an app's folder. The
     * command must know how much to read: the input is sent whole and then left open.
     */
    suspend fun exec(command: String, input: ByteArray): String? = exec(command, input.inputStream())

    /** As the other [exec], reading [input] as it goes: a package need not fit in memory whole. */
    suspend fun exec(command: String, input: InputStream): String? = null
}

/** What Lumbina does with that access; shared by the settings screen and the remote's process. */
data class AccessSettings(
    /** Holding OK on an app in the switcher stops it. */
    val closeFromRecents: Boolean = true,
    /** Open Lumbina when the device turns on. */
    val openOnPowerOn: Boolean = false,
    /**
     * The first-run setup was finished or skipped; its steps stay in the settings. The name is
     * from when the setup was a card on the home screen with "Hide" on it.
     */
    val setupCardHidden: Boolean = false,
    /**
     * The user's answers about apps that record the microphone themselves: true lets the app
     * hear the remote, false keeps it from it. An app not here has not been asked about.
     */
    val micApps: Map<String, Boolean> = emptyMap(),
    /** The setup was asked back after it was over: it opens at its steps, with nobody to welcome. */
    val setupReopened: Boolean = false,
    /**
     * The user put away the card saying debugging is off: it is not shown again until debugging
     * is seen on, and so off again later (the firmware's doing rather than the user's).
     */
    val debuggingOffDismissed: Boolean = false,
)

typealias AccessSettingsRepository = SettingsRepository<AccessSettings>

/**
 * The things Lumbina does over [LocalAdb]. Keeping the remote's service on is the system's
 * WRITE_SECURE_SETTINGS permission: with it, [heal] turns the service back on whenever the
 * firmware drops it.
 */
// One function per thing Lumbina asks of the device's shell; splitting them would only scatter the commands.
@Suppress("TooManyFunctions")
class LocalAccess(
    private val adb: LocalAdb,
    private val secureSettings: SecureSettings,
    /** Brings the service on (HealAccessibilityService) and tells how it went. */
    private val heal: suspend () -> HealResult,
    private val packageName: String,
    /** The class of Lumbina's notification listener. */
    private val notificationListener: String,
    /** Apps Lumbina carries for devices that lack a part of the system. */
    private val companions: List<CompanionApp> = emptyList(),
) {
    suspend fun state(): AdbState = adb.state()

    /**
     * Connects (asking the user if needed) and sets Lumbina up: the service kept on, usage
     * statistics, notifications, and the companion apps the device misses or has older copies of.
     */
    suspend fun connectAndSetUp(): SetupResult = when (adb.connect()) {
        AdbState.Connected -> setUp()
        AdbState.NotAllowed -> SetupResult.NotAllowed
        AdbState.Off -> SetupResult.Off
        AdbState.NotListening -> SetupResult.NotListening
        AdbState.Failed -> SetupResult.Failed
    }

    private suspend fun setUp(): SetupResult {
        val kept = grantSecureSettings(true)
        if (!kept && adb.state() == AdbState.NotAllowed) return SetupResult.AllowedOnce
        renew()
        if (!kept) return SetupResult.NotKept
        heal()
        return SetupResult.Done
    }

    /**
     * What a connected Lumbina sets up besides keeping its service on, done again: a version that
     * learnt to set up something new does it for those who connected before. Never asks the user;
     * false when the commands didn't run (not connected, or the shell failed or timed out). The
     * companions are seen to while connected even if the commands timed out.
     */
    suspend fun renew(): Boolean {
        val commands = listOf(
            usageStats(),
            // Also for those who connected before a version that lifts it: their service stays off the list till then.
            unrestricted(),
            // Notifications for the bell and "now playing": firmware that needs this connection has
            // no screen where the user could allow Lumbina's listener.
            notificationAccess(),
        )
        // Done at every opening of Lumbina, in one connection (each costs a handshake, slow on a Mi
        // Box): the log hears of it only when it fails. One failing doesn't stop the next.
        val ran = adb.shellQuietly(commands.joinToString(SHELL_AND_THEN)) != null
        if (ran || adb.state() == AdbState.Connected) companions.filter { it.wanted() }.forEach { it.install(adb) }
        return ran
    }

    val keepsService: Boolean get() = secureSettings.canWrite

    /**
     * The same permissions from a computer, for a device whose debugging lets only a computer in
     * ([AdbState.NotListening]). Companions and the speech recognizer are not set up this way.
     */
    val computerCommands: List<String>
        get() = listOf(
            "pm grant $packageName $WRITE_SECURE_SETTINGS",
            unrestricted(),
            usageStats(),
            notificationAccess(),
        ).map { "adb shell $it" }

    private fun notificationAccess() = "cmd notification allow_listener $packageName/$notificationListener"

    /** Gives or takes back the permission, and with it brings the service on. False when not connected or refused. */
    suspend fun setKeepService(on: Boolean): Boolean {
        if (!grantSecureSettings(on)) return false
        if (on) bringServiceOn()
        return true
    }

    private suspend fun bringServiceOn() {
        adb.shell(unrestricted())
        heal()
    }

    /**
     * Android 13+ restricts an app installed from a file (a browser, a file manager): the system
     * strips its accessibility service from the list instead of binding it (seen on Vidda C3S).
     * Allowed from the shell, the service binds as for an app installed over adb. Older Android
     * doesn't know the operation and only prints an error.
     */
    private fun unrestricted() = unrestricted(packageName)

    /** `pm` prints its error rather than failing the shell, so the permission itself tells whether it took. */
    private suspend fun grantSecureSettings(on: Boolean): Boolean {
        val verb = if (on) "grant" else "revoke"
        return adb.shell("pm $verb $packageName $WRITE_SECURE_SETTINGS") != null &&
            secureSettings.canWrite == on
    }

    suspend fun grantUsageStats() {
        adb.shell(usageStats())
    }

    private fun usageStats() = "appops set $packageName GET_USAGE_STATS allow"

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
        // The caller logs what it started; the command, with the APK's path, only when it fails.
        return adb.shellQuietly("setsid sh -c \"$program </dev/null >/dev/null 2>&1 &\"") != null
    }

    /** Stops [app] as if from the system's app info; false without access. */
    suspend fun forceStop(app: String): Boolean = app != packageName && adb.shell("am force-stop $app") != null

    /** Holds the key with this code down as a finger would: an accessibility service can press keys, not hold them. */
    suspend fun holdKey(keyCode: Int): Boolean = adb.shell("input keyevent --longpress $keyCode") != null
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
            "adb shell ${unrestricted(packageName)}",
            "adb shell settings put secure enabled_accessibility_services ${AccessibilityServiceList.join(services)}",
            "adb shell pm grant $packageName $WRITE_SECURE_SETTINGS",
        )
    }
}

private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

/** Lifts Android 13's restriction on an app installed from a file, see [LocalAccess]. */
private fun unrestricted(packageName: String) = "appops set $packageName ACCESS_RESTRICTED_SETTINGS allow"

/** Commands in one shell, each run whatever the one before gave. */
internal const val SHELL_AND_THEN = " ; "
