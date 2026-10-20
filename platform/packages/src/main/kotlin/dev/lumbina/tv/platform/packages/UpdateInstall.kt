package dev.lumbina.tv.platform.packages

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.update.Release
import dev.lumbina.tv.core.domain.update.ShellInstall
import dev.lumbina.tv.core.domain.update.UpdateStep
import java.io.File
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/** What an install is made of, each done by the part of the device that does it. */
internal interface InstallSteps {
    /** Whether Lumbina's own connection to the device's debugging is up. */
    suspend fun overAdb(): Boolean

    fun mayInstallApps(): Boolean

    fun canAskToInstallApps(): Boolean

    /** Opens the device's screen to let Lumbina install apps; false where it did not open. */
    fun askToInstallApps(): Boolean

    /** See [ReleaseDownload.fetch]. */
    suspend fun fetch(release: Release, file: File, progress: suspend (Float) -> Unit): Boolean

    /** See [PackageCheck.unfit]. */
    fun unfit(file: File): UpdateStep?

    suspend fun installOverAdb(file: File): ShellInstall

    /** See [AdbSelfInstall.stepAfter]. */
    fun stepAfter(answer: ShellInstall): UpdateStep?

    /** See [SystemInstaller.awaitInstallable]. */
    suspend fun awaitInstallable(asked: Boolean)

    suspend fun installThroughSystem(file: File): UpdateStep
}

/**
 * One install of a release into its package file, told as [UpdateStep]s: the download (asking first, where
 * Lumbina may not install apps yet, so the user allows it meanwhile), the checks, then over adb
 * where it can, else, or when that fails, through the system's installer once its question can come.
 * A checked package, or one cut short by the network, stays in that file for the next try; one that is
 * no good, refused, or could not be written goes.
 */
internal class UpdateInstall(
    private val steps: InstallSteps,
    private val log: LumbinaLog,
    private val askAfterMillis: Long = ASK_AFTER_MILLIS,
) {
    fun run(release: Release, file: File): Flow<UpdateStep> = flow {
        var keep = true
        try {
            keep = install(release, file)
        } catch (e: FinalAnswer) {
            keep = false
            failed(release, e)
        } catch (e: IOException) {
            failed(release, e)
        } finally {
            if (!keep) file.delete()
        }
    }

    private suspend fun FlowCollector<UpdateStep>.failed(release: Release, e: IOException) {
        log.log(UPDATE_TAG, "${release.apkName}: $e")
        emit(UpdateStep.Failed)
    }

    /** False when [file] is no good to keep. */
    private suspend fun FlowCollector<UpdateStep>.install(release: Release, file: File): Boolean {
        val adb = steps.overAdb()
        emit(UpdateStep.Downloading(0f))
        val asked = !adb && askToInstallApps(0f)
        val fetched = steps.fetch(release, file) {
            emit(UpdateStep.Downloading(it, needsPermission = asked && !steps.mayInstallApps()))
        }
        (if (fetched) steps.unfit(file) else UpdateStep.Corrupt)?.let {
            emit(it)
            return false
        }
        var waits = asked
        if (adb) {
            emit(UpdateStep.InstallingItself)
            val answer = steps.installOverAdb(file)
            if (answer != ShellInstall.Failed) {
                steps.stepAfter(answer)?.let { emit(it) }
                return answer != ShellInstall.Refused
            }
            // That way failed: the system's installer takes it, and may want Lumbina allowed first.
            waits = askToInstallApps(1f)
        }
        if (waits && !steps.mayInstallApps()) {
            emit(UpdateStep.NeedsPermission)
            log.log(UPDATE_TAG, "waiting to be allowed to install apps")
        }
        steps.awaitInstallable(waits)
        emit(UpdateStep.Installing)
        val step = steps.installThroughSystem(file)
        emit(step)
        return step != UpdateStep.Refused
    }

    /**
     * Where Lumbina may not install apps yet and the device has the screen to allow it: says on the
     * row why that screen comes (the download at [fraction]), then opens it. True when it opened.
     */
    private suspend fun FlowCollector<UpdateStep>.askToInstallApps(fraction: Float): Boolean {
        if (steps.mayInstallApps() || !steps.canAskToInstallApps()) return false
        emit(UpdateStep.Downloading(fraction, needsPermission = true))
        delay(askAfterMillis)
        return steps.askToInstallApps().also { if (it) log.log(UPDATE_TAG, "asked to be allowed to install apps") }
    }

    private companion object {
        /** How long the row says why the device's screen comes, before it does. */
        const val ASK_AFTER_MILLIS = 1_500L
    }
}
