package dev.lumbina.tv.platform.packages

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAdb
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.update.ShellInstall
import dev.lumbina.tv.core.domain.update.UpdateStep
import dev.lumbina.tv.core.domain.update.shellInstallAnswer
import dev.lumbina.tv.core.domain.update.shellInstallCommand
import dev.lumbina.tv.core.domain.update.shellInstallResult
import java.io.File
import javax.inject.Inject

/**
 * Lumbina putting its own package on the device over its own connection to the device's
 * debugging, as `adb install` would, with nothing asked: the user agreed to that connection, and a
 * firmware's own question may be in a language they can't read.
 */
internal class AdbSelfInstall @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profile: DeviceProfile,
    private val adb: LocalAdb,
    private val log: LumbinaLog,
) {
    /** Whether this device has such a connection, and it is up. */
    suspend fun available(): Boolean = profile.localAdb && adb.state() == AdbState.Connected

    suspend fun install(file: File): ShellInstall {
        // What to start once replaced: Lumbina's own launcher activity, whatever the app names it.
        val start = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
            ?: return ShellInstall.Failed
        val command = shellInstallCommand(file.length(), start.flattenToShortString())
        val answer = file.inputStream().use { adb.exec(command, it) }
        return shellInstallResult(answer).also {
            log.log(UPDATE_TAG, "over adb: $it ${answer?.let(::shellInstallAnswer)?.take(ANSWER_CHARS)}")
        }
    }

    /** What to say after an install over adb that did not fail; null when nothing is left to say. */
    fun stepAfter(answer: ShellInstall): UpdateStep? = when (answer) {
        // Normally never seen: the install stops this process first.
        ShellInstall.Done, ShellInstall.Failed -> null

        ShellInstall.Refused -> UpdateStep.Refused

        // The device may still finish it, and then Lumbina restarts; if not, OK tries again.
        ShellInstall.Pending -> UpdateStep.NotInstalled
    }

    private companion object {
        const val ANSWER_CHARS = 200
    }
}
