package dev.lumbina.tv.platform.system

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.diagnostics.ReportExporter
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Saves reports to the shared Download folder (no permission needed on Android 10+), where a
 * file manager or adb can reach them; older Android gets the app's external files folder.
 *
 * TODO(before publishing): the report carries the log's tail, and the log holds links opened from
 *  the phone, uploaded file names and recognized phrases. Log their kind and length instead, or
 *  leave them out of the report, before builds go to other people.
 */
internal class AndroidReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) : ReportExporter {

    override fun save(fileName: String, text: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) toDownloads(fileName, text) else toAppFolder(fileName, text)
    } catch (e: IOException) {
        failed(e)
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
        failed(e)
    }

    private fun failed(e: Exception): String? {
        log.log(TAG, "report not saved: $e")
        return null
    }

    /** Inserts a Downloads row, removes it again if the write fails, and returns where it landed. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun toDownloads(fileName: String, text: String): String? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val written = try {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        if (!written) {
            resolver.delete(uri, null, null)
            return null
        }
        return savedPath(uri) ?: "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
    }

    /** The provider may rename the file on a name clash, so ask it for the final path. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savedPath(uri: Uri): String? {
        val columns = arrayOf(MediaStore.Downloads.RELATIVE_PATH, MediaStore.Downloads.DISPLAY_NAME)
        return context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val folder = cursor.getString(0)?.trimEnd('/') ?: Environment.DIRECTORY_DOWNLOADS
            cursor.getString(1)?.let { "$folder/$it" }
        }
    }

    private fun toAppFolder(fileName: String, text: String): String? {
        val dir = context.getExternalFilesDir(null) ?: return null
        val file = File(dir, fileName).apply { writeText(text) }
        return file.absolutePath
    }

    private companion object {
        const val TAG = "diagnostics"
    }
}
