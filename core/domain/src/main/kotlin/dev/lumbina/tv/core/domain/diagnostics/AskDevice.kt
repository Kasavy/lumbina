package dev.lumbina.tv.core.domain.diagnostics

import dev.lumbina.tv.core.domain.MILLIS_PER_SECOND
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAdb
import dev.lumbina.tv.core.domain.device.DeviceAnswer
import dev.lumbina.tv.core.domain.device.DeviceProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds out for a diagnostics report what a tester would otherwise be asked to type into adb: the
 * firmware's services asked directly ([DeviceProfile.diagnostics]), then the shell over Lumbina's
 * own connection, on devices that have one.
 */
class AskDevice(
    private val profile: DeviceProfile,
    private val adb: LocalAdb,
    private val packageName: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val probeMillis: Long = 20_000,
) {
    suspend operator fun invoke(): List<DeviceAnswer> = probe() + shell()

    /**
     * The probe blocks on the firmware's sockets, and a connect to a service that hangs can't be
     * interrupted: past the deadline the report goes without it, and the probe is left to end by itself.
     */
    private suspend fun probe(): List<DeviceAnswer> {
        val diagnostics = profile.diagnostics ?: return emptyList()
        val asked = CoroutineScope(dispatcher).async { diagnostics.probe() }
        return withTimeoutOrNull(probeMillis) { asked.await() }
            ?: listOf(DeviceAnswer("probe", "no answer in ${probeMillis / MILLIS_PER_SECOND} s"))
    }

    /** Each command is a connection of its own: without one nothing is asked, and the first to fail is the last. */
    private suspend fun shell(): List<DeviceAnswer> {
        if (!profile.localAdb) return emptyList()
        val state = adb.state()
        if (state != AdbState.Connected) return listOf(DeviceAnswer("shell", "not asked: debugging connection $state"))
        val answers = mutableListOf<DeviceAnswer>()
        for (command in COMMANDS + accessibility() + profile.diagnostics?.shellCommands.orEmpty()) {
            val answer = adb.shell(command)
            answers += DeviceAnswer(command, answer?.let(::shortened) ?: "no connection")
            if (answer == null) break
        }
        return answers
    }

    /**
     * Why the remote's service may not run: the list the system keeps and what it made of it, and
     * whether Lumbina came from a file, which Android 13+ restricts (the installer and the
     * restriction's state).
     */
    private fun accessibility() = listOf(
        "settings get secure enabled_accessibility_services",
        "dumpsys accessibility | grep -E 'Enabled services|Binding services|Crashed services'",
        "appops get $packageName ACCESS_RESTRICTED_SETTINGS",
        "dumpsys package $packageName | grep -E 'installerPackageName|initiatingPackageName|packageSource'",
    )

    /** A service dump runs to hundreds of lines; its head says what a report needs. */
    private fun shortened(answer: String): String {
        val lines = answer.trimEnd().lines()
        if (lines.size <= ANSWER_LINES) return answer
        return (lines.take(ANSWER_LINES) + "… ${lines.size} lines").joinToString("\n")
    }

    private companion object {
        const val ANSWER_LINES = 60

        /**
         * Asked of any device: which apps offer themselves as the home screen, and which packages are
         * turned off (a firmware service Lumbina leans on may be among them).
         */
        val COMMANDS = listOf(
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
            "pm list packages -d",
        )
    }
}
