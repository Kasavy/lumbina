package dev.lumbina.tv.tvprovider

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * For firmware with logcat disabled, writes and refusals go to `tvprovider.log` in the app's
 * external files (`/sdcard/Android/data/dev.lumbina.tv.tvprovider/files`); kept under [MAX_BYTES].
 * Lumbina's diagnostics report asks for the end of it ([tail], through the provider: on Android 11
 * the shell can't read that folder) and counts the lines by their shape, "date time package action
 * …" (`companionLogLines` in Lumbina): a change of shape goes there too.
 */
internal object CompanionLog {
    private const val MAX_BYTES = 256 * 1024L
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    fun init(context: Context) {
        if (file == null) file = context.getExternalFilesDir(null)?.let { File(it, "tvprovider.log") }
    }

    @Synchronized
    fun log(message: String) {
        val target = file ?: return
        runCatching {
            if (target.length() > MAX_BYTES) target.renameTo(File(target.parentFile, "tvprovider.1.log"))
            target.appendText("${time.format(Date())} $message\n")
        }
    }

    /** The last [chars] of the log, the older file first, from a whole line on; empty when there is none. */
    @Synchronized
    fun tail(chars: Int): String {
        val target = file ?: return ""
        val text = listOf(File(target.parentFile, "tvprovider.1.log"), target)
            .joinToString("") { runCatching { it.readText() }.getOrDefault("") }
        return tailOf(text, chars)
    }
}

/** The whole lines of [text] that fit in its last [chars]. */
internal fun tailOf(text: String, chars: Int): String {
    if (text.length <= chars) return text
    val start = text.length - chars
    // Cut where a line begins; past the last line break nothing whole is left.
    if (text[start - 1] == '\n') return text.substring(start)
    val next = text.indexOf('\n', start)
    return if (next < 0) "" else text.substring(next + 1)
}
