package dev.lumbina.tv.platform.tvprovider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.CompanionApp
import dev.lumbina.tv.core.domain.access.LocalAdb
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lumbina TV Provider, carried in Lumbina's assets for devices with no TV provider of their own
 * (where apps would have nowhere to publish channels and "continue watching").
 */
internal class TvProviderCompanion @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channels: AndroidTvChannels,
    private val log: LumbinaLog,
) : CompanionApp {

    /** In the app's external files: the adb shell can read there, and not in the app's own folder. */
    private val staged: File? get() = context.getExternalFilesDir(null)?.let { File(it, STAGED) }

    override fun missing(): Boolean = !channels.isAvailable() && carried()

    private fun carried(): Boolean = try {
        context.assets.open(ASSET).close()
        true
    } catch (_: IOException) {
        false
    }

    /** `pm` reads the APK from the shell's input: it can't open files in an app's folder itself. */
    override suspend fun install(adb: LocalAdb): Boolean {
        val apk = stage() ?: return false
        val installed = try {
            adb.shell("pm install -r -S ${apk.length()} < '${apk.absolutePath}'")?.contains("Success") == true
        } finally {
            apk.delete()
        }
        log.log(TAG, if (installed) "Lumbina TV Provider installed" else "Lumbina TV Provider not installed")
        if (installed) channels.onGranted()
        return installed
    }

    /** Megabytes to copy: off the caller's thread. Null when it can't be put there. */
    private suspend fun stage(): File? = withContext(Dispatchers.IO) {
        val file = staged ?: return@withContext null
        try {
            context.assets.open(ASSET).use { apk -> file.outputStream().use(apk::copyTo) }
            file
        } catch (e: IOException) {
            log.log(TAG, "TV provider not staged: $e")
            null
        }
    }

    private companion object {
        const val TAG = "channels"
        const val ASSET = "companion/tvprovider.apk"
        const val STAGED = "tvprovider.apk"
    }
}
