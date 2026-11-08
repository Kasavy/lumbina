package dev.lumbina.tv.platform.adb

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAdb
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [LocalAdb] over the device's own adbd on the loopback address, one short connection per call.
 * The key lives in the app's files, shared by the UI and the remote's process.
 */
@Singleton
internal class AndroidLocalAdb @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) : LocalAdb {

    private val mutex = Mutex()
    private val key by lazy { AdbKey.loadOrCreate(File(context.filesDir, KEY_DIR)) }

    override suspend fun state(): AdbState = open(offerKey = false) { AdbState.Connected }.state

    override suspend fun connect(): AdbState {
        val result = open(offerKey = true) { AdbState.Connected }
        log.log(TAG, "connect: ${result.state}")
        return result.state
    }

    override suspend fun shell(command: String): String? {
        val result = open(offerKey = false) { it.shell(command, SHELL_TIMEOUT_MILLIS) }
        log.log(TAG, "shell \"$command\": ${if (result.value != null) "ok" else result.state}")
        return result.value
    }

    override suspend fun shellQuietly(command: String): String? {
        val result = open(offerKey = false) { it.shell(command, SHELL_TIMEOUT_MILLIS) }
        // Debugging off or not let in is the device's state, said elsewhere: not one line per opening.
        if (result.value == null && result.state !in QUIET) log.log(TAG, "shell \"$command\": ${result.state}")
        return result.value
    }

    override suspend fun exec(command: String, input: InputStream): String? {
        val result = open(offerKey = false) { it.exec(command, input, EXEC_TIMEOUT_MILLIS) }
        log.log(TAG, "exec \"$command\": ${if (result.value != null) "ok" else result.state}")
        return result.value
    }

    private class Result<T>(val state: AdbState, val value: T? = null)

    private suspend fun <T> open(offerKey: Boolean, use: (AdbConnection) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val connection = AdbConnection.open(HOST, PORT, key, offerKey, PROMPT_MILLIS)
                        ?: return@withLock Result(AdbState.NotAllowed)
                    connection.use { Result(AdbState.Connected, use(it)) }
                } catch (_: ConnectException) {
                    Result(unreached(refused = true, debuggingOn()))
                } catch (e: IOException) {
                    val state = unreached(refused = false, debuggingOn())
                    if (state == AdbState.Failed) log.log(TAG, "connection: $e")
                    Result(state)
                } catch (e: GeneralSecurityException) {
                    // Lumbina's key or the challenge's signature: debugging is on, the failure is ours.
                    log.log(TAG, "key: $e")
                    Result(AdbState.Failed)
                }
            }
        }

    /** "USB debugging" as Android's settings have it. */
    private fun debuggingOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    private companion object {
        const val TAG = "adb"

        /** States a quiet shell doesn't log: the device's, told by the pages that need debugging. */
        val QUIET = setOf(AdbState.Off, AdbState.NotListening, AdbState.NotAllowed)
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val KEY_DIR = "adb"
        const val PROMPT_MILLIS = 60_000
        const val SHELL_TIMEOUT_MILLIS = 15_000

        // What takes input is an install; it says something every second while it waits, but a slow
        // device may take a while over writing the package before that.
        const val EXEC_TIMEOUT_MILLIS = 120_000
    }
}

/**
 * The state when Lumbina couldn't get through to debugging: [refused] (nothing listens on the port)
 * or the connection timed out or closed. With "USB debugging" off that is the reason; with it on, a
 * refusal means the device opens it to a computer only, and a dropped connection is a failure whose
 * cause the log has ("turn debugging on" would be wrong advice).
 */
internal fun unreached(refused: Boolean, debuggingOn: Boolean): AdbState = when {
    !debuggingOn -> AdbState.Off
    refused -> AdbState.NotListening
    else -> AdbState.Failed
}
