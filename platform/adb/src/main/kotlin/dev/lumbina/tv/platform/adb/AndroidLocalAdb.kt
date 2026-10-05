package dev.lumbina.tv.platform.adb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAdb
import java.io.File
import java.io.IOException
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

    private class Result<T>(val state: AdbState, val value: T? = null)

    private suspend fun <T> open(offerKey: Boolean, use: (AdbConnection) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val connection = AdbConnection.open(HOST, PORT, key, offerKey, PROMPT_MILLIS)
                        ?: return@withLock Result(AdbState.NotAllowed)
                    connection.use { Result(AdbState.Connected, use(it)) }
                } catch (_: IOException) {
                    Result(AdbState.Off)
                } catch (e: GeneralSecurityException) {
                    // Lumbina's key or the challenge's signature: debugging is on, the failure is ours.
                    log.log(TAG, "key: $e")
                    Result(AdbState.Failed)
                }
            }
        }

    private companion object {
        const val TAG = "adb"
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val KEY_DIR = "adb"
        const val PROMPT_MILLIS = 60_000
        const val SHELL_TIMEOUT_MILLIS = 15_000
    }
}
