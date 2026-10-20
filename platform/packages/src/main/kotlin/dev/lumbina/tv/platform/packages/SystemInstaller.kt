package dev.lumbina.tv.platform.packages

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.update.UpdateStep
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** The system's installer, which asks the user before it puts a package on the device. */
internal class SystemInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) {
    /**
     * Gives [file] to the system's installer and waits for its refusal, which comes to a step to show:
     * on success this process is stopped before anything is said. Cancelled (some firmware never
     * answers), it gives the session up.
     */
    suspend fun install(file: File): UpdateStep {
        val status = statusOf(file)
        log.log(UPDATE_TAG, "installer: $status")
        return when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateStep.Cancelled

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            -> UpdateStep.Refused

            else -> UpdateStep.NotInstalled
        }
    }

    private suspend fun statusOf(file: File): Int {
        val installer = context.packageManager.packageInstaller
        // What was said of a session nobody waits for any more.
        while (UpdateReceiver.results.tryReceive().isSuccess) Unit
        val id = try {
            commit(installer, file)
        } catch (e: SecurityException) {
            log.log(UPDATE_TAG, "installer: $e")
            return PackageInstaller.STATUS_FAILURE
        } catch (e: IllegalStateException) {
            log.log(UPDATE_TAG, "installer: $e")
            return PackageInstaller.STATUS_FAILURE
        }
        try {
            while (true) {
                val (session, status) = UpdateReceiver.results.receive()
                if (session == id) return status
            }
        } catch (e: CancellationException) {
            try {
                installer.abandonSession(id)
            } catch (_: SecurityException) {
                // Already over.
            }
            throw e
        }
    }

    /** Whether the device lets Lumbina install apps; without that the installer first sends the user to allow it. */
    fun mayInstallApps(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Whether the device has the screen where Lumbina may be allowed to install apps. */
    fun canAskToInstallApps(): Boolean = context.packageManager.resolveActivity(allowScreen(), 0) != null

    /** Opens the device's screen where Lumbina may be allowed to install apps; false where it has none. */
    fun askToInstallApps(): Boolean {
        val screen = allowScreen()
        return try {
            context.startActivity(screen)
            true
        } catch (e: ActivityNotFoundException) {
            // Firmware without the screen (Hisense turns the settings app off): the installer asks on its own.
            log.log(UPDATE_TAG, "no screen to allow installing apps: $e")
            false
        }
    }

    private fun allowScreen() =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Returns once Lumbina is on screen again and, when the user was sent to allow it ([asked]), may
     * install apps. Its question can only come over Lumbina: Android 10 and newer keep an app behind
     * the settings from opening it (seen on Google TV 14), and the install would wait for an answer
     * nobody can give. The permission is looked at each time Lumbina comes back.
     */
    suspend fun awaitInstallable(
        asked: Boolean,
        state: StateFlow<Lifecycle.State> = ProcessLifecycleOwner.get().lifecycle.currentStateFlow,
    ) {
        while (true) {
            state.first { it.isAtLeast(Lifecycle.State.STARTED) }
            if (!asked || mayInstallApps()) return
            state.first { !it.isAtLeast(Lifecycle.State.STARTED) }
        }
    }

    /** Writes [file] into a new session and asks for it to be installed; returns the session's id. */
    private fun commit(installer: PackageInstaller, file: File): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = installer.createSession(params)
        var committed = false
        try {
            write(installer, id, file)
            committed = true
        } finally {
            // Failed on the way: the session is given up rather than left for the system to clear.
            if (!committed) installer.abandonSession(id)
        }
        return id
    }

    private fun write(installer: PackageInstaller, id: Int, file: File) {
        installer.openSession(id).use { session ->
            session.openWrite("lumbina", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setPackage(context.packageName)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            session.commit(pending.intentSender)
        }
    }
}

/**
 * Hears from the system's installer: it wants the user's word (then its question is put on
 * screen), or it is done with a refusal, which goes to whoever waits in [results] as the
 * session's id and the status.
 */
internal class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            results.trySend(session to status)
            return
        }
        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        try {
            context.startActivity(checkNotNull(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) {
            // No question to show, or nothing allowed to show it: nobody will answer, so that is the answer.
            results.trySend(session to PackageInstaller.STATUS_FAILURE)
        }
    }

    companion object {
        val results = Channel<Pair<Int, Int>>(Channel.CONFLATED)
    }
}
