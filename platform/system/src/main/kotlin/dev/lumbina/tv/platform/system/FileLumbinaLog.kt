package dev.lumbina.tv.platform.system

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.diagnostics.LogArchive
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs to logcat and to `Android/data/<pkg>/files/logs/lumbina.log` (readable over adb without
 * root), for firmware with logcat disabled. Keeps one rotated file. Writes on a thread of its
 * own, so callers (e.g. the remote's key handling) never wait for the disk; every process
 * appends under a file lock.
 */
@Singleton
internal class FileLumbinaLog @Inject constructor(@ApplicationContext private val context: Context) :
    LumbinaLog,
    LogArchive {

    private val file: File? by lazy { context.getExternalFilesDir("logs")?.let { File(it, "lumbina.log") } }
    private val process: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // "dev.lumbina.tv:ui" is "ui"; a prefix the build put before it (processNames in build-logic)
        // is dropped as far as it ends with a dot.
        Application.getProcessName().substringAfter(':').substringAfterLast('.')
    } else {
        "?"
    }
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "lumbina-log").apply { isDaemon = true } }

    override fun log(tag: String, message: String) {
        Log.i("Lumbina/$tag", message)
        val at = System.currentTimeMillis()
        writer.execute { append("${format.format(Date(at))} [$process] $tag: $message\n") }
    }

    private fun append(line: String) {
        val target = file ?: return
        runCatching {
            RandomAccessFile(File(target.parentFile, "lumbina.lock"), "rw").use { lock ->
                lock.channel.lock().use {
                    if (target.length() > MAX_BYTES) target.renameTo(File(target.parentFile, "lumbina.1.log"))
                    target.appendText(line)
                }
            }
        }
    }

    /** The last [lines] lines across the current and the rotated file. */
    @Synchronized
    override fun tail(lines: Int): List<String> {
        val current = file ?: return emptyList()
        val older = File(current.parentFile, "lumbina.1.log")
        val all = listOf(older, current)
            .filter { it.isFile }
            .flatMap { runCatching { it.readLines() }.getOrDefault(emptyList()) }
        return all.takeLast(lines)
    }

    private companion object {
        const val MAX_BYTES = 512 * 1024L
    }
}
