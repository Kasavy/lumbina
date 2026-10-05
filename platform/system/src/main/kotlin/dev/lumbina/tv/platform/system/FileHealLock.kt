package dev.lumbina.tv.platform.system

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.health.HealLock
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [HealLock] over a file lock in the app's files, which both of Lumbina's processes see. A process
 * may hold a file's lock only once, so heals within one process queue on a [Mutex] first. The
 * other process may hold the file for a whole heal (seconds), so the wait polls and can be cancelled.
 */
@Singleton
internal class FileHealLock @Inject constructor(@ApplicationContext context: Context) : HealLock {
    private val file = File(context.filesDir, "heal.lock")
    private val inProcess = Mutex()

    override suspend fun <T> withLock(block: suspend () -> T): T = inProcess.withLock {
        val channel = withContext(Dispatchers.IO) { RandomAccessFile(file, "rw").channel }
        channel.use {
            val held = acquire(it)
            try {
                block()
            } finally {
                held.release()
            }
        }
    }

    private suspend fun acquire(channel: FileChannel): FileLock {
        while (true) {
            withContext(Dispatchers.IO) { channel.tryLock() }?.let { return it }
            delay(POLL_MILLIS)
        }
    }

    private companion object {
        const val POLL_MILLIS = 200L
    }
}
