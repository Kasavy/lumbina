package dev.lumbina.tv.platform.packages

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel

/** The system's installer, which asks the user before it puts a package on the device. */
internal class SystemInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) {
    /**
     * Gives [file] to the system's installer and waits for its refusal: on success this process is
     * stopped before anything is said. Cancelled (some firmware never answers), it gives the session up.
     */
    suspend fun install(file: File): Int {
        val installer = context.packageManager.packageInstaller
        // What was said of a session nobody waits for any more.
        while (UpdateReceiver.results.tryReceive().isSuccess) Unit
        val id = try {
            commit(installer, file)
        } catch (e: SecurityException) {
            log.log(TAG, "installer: $e")
            return PackageInstaller.STATUS_FAILURE
        } catch (e: IllegalStateException) {
            log.log(TAG, "installer: $e")
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
            // The session holds it now; an install that goes through leaves no chance to delete it later.
            file.delete()
            val intent = Intent(context, UpdateReceiver::class.java).setPackage(context.packageName)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            session.commit(pending.intentSender)
        }
    }

    private companion object {
        const val TAG = "update"
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
