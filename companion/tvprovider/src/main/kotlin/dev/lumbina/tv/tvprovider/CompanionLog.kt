package dev.lumbina.tv.tvprovider

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * For firmware with logcat disabled, writes and refusals go to `tvprovider.log` in the app's
 * external files (`/sdcard/Android/data/dev.lumbina.tv.tvprovider/files`); kept under [MAX_BYTES].
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
}
