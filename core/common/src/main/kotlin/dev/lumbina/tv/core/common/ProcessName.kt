package dev.lumbina.tv.core.common

import java.io.File
import java.io.IOException

/**
 * This process's name, "dev.lumbina.tv:ui" and the like, on any Android: Application.getProcessName
 * is API 28, and a report from Android 8 (Skyworth G6) had "?" for every line. Null if unreadable.
 */
fun currentProcessName(): String? = try {
    processNameOf(File("/proc/self/cmdline").readBytes())
} catch (_: IOException) {
    null
}

/** The first argument of a process's command line, as /proc gives it (each ends with a zero byte). */
internal fun processNameOf(cmdline: ByteArray): String? =
    cmdline.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8).trim().ifEmpty { null }
