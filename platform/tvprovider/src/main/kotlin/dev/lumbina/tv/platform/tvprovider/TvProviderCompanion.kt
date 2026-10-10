package dev.lumbina.tv.platform.tvprovider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.CompanionApp
import dev.lumbina.tv.core.domain.access.LocalAdb
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

    override fun missing(): Boolean = !channels.isAvailable() && carried()

    private fun carried(): Boolean = try {
        context.assets.open(ASSET).close()
        true
    } catch (_: IOException) {
        false
    }

    /**
     * The APK goes to `pm` through the connection, as `adb install` sends it: a file would have to
     * lie where the shell can read, and on some firmware that is not an app's folder.
     */
    override suspend fun install(adb: LocalAdb): Boolean {
        val apk = try {
            withContext(Dispatchers.IO) { context.assets.open(ASSET).use { it.readBytes() } }
        } catch (e: IOException) {
            log.log(TAG, "Lumbina TV Provider not read: $e")
            return false
        }
        val answer = adb.exec("cmd package install -r -S ${apk.size}", apk)
        val installed = answer?.contains("Success") == true
        // pm's answer is the only place the reason shows: a signature clash, no space, a firmware's refusal.
        val why = if (installed) "" else ": ${answer?.trim()?.take(ANSWER_CHARS) ?: "no connection"}"
        log.log(TAG, "Lumbina TV Provider ${if (installed) "installed" else "not installed"}$why")
        if (installed) channels.onGranted()
        return installed
    }

    private companion object {
        const val TAG = "channels"
        const val ASSET = "companion/tvprovider.apk"
        const val ANSWER_CHARS = 200
    }
}
