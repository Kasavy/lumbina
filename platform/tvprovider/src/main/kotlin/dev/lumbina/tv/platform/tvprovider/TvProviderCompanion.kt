package dev.lumbina.tv.platform.tvprovider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.CompanionApp
import dev.lumbina.tv.core.domain.access.LocalAdb
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lumbina TV Provider, carried in Lumbina's assets for devices with no TV provider of their own
 * (where apps would have nowhere to publish channels and "continue watching").
 */
@Singleton
internal class TvProviderCompanion @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channels: AndroidTvChannels,
    private val log: LumbinaLog,
) : CompanionApp {

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** Setup and a connect may both renew at once: one install at a time, the second finds nothing to do. */
    private val installing = Mutex()

    /** The carried APK's version code, written beside it by the build; null when Lumbina carries none. */
    private var carried: Long? = null

    private fun carried(): Long? = carried ?: try {
        context.assets.open(VERSION_ASSET).bufferedReader().use { it.readText().trim().toLongOrNull() }
            ?.also { carried = it }
    } catch (_: IOException) {
        null
    }

    override suspend fun wanted(): Boolean = withContext(Dispatchers.IO) {
        companionWanted(
            provider = channels.provider(),
            installed = { companionVersion(context.packageManager) },
            carried = carried(),
            refused = prefs.getLong(REFUSED, 0L).takeIf { it > 0 },
        )
    }

    /**
     * The APK goes to `pm` through the connection, as `adb install` sends it: a file would have to
     * lie where the shell can read, and on some firmware that is not an app's folder.
     */
    override suspend fun install(adb: LocalAdb): Boolean = installing.withLock {
        if (!wanted()) return@withLock true
        val version = carried() ?: return@withLock false
        val apk = try {
            withContext(Dispatchers.IO) { context.assets.open(APK_ASSET).use { it.readBytes() } }
        } catch (e: IOException) {
            log.log(TAG, "Lumbina TV Provider not read: $e")
            return@withLock false
        }
        val update = channels.provider() == AndroidTvChannels.COMPANION
        val answer = adb.exec(companionInstallCommand(apk.size, Build.VERSION.SDK_INT), apk)
        val installed = answer?.contains("Success") == true
        // pm's answer is the only place the reason shows: a signature clash, no space, a firmware's refusal.
        val why = if (installed) "" else ": ${answer?.trim()?.take(ANSWER_CHARS) ?: "no connection"}"
        log.log(TAG, "Lumbina TV Provider $version ${if (installed) "installed" else "not installed"}$why")
        // An update pm refused is not tried again at each start: the next Lumbina carries another version.
        if (update && answer != null) prefs.edit { if (installed) remove(REFUSED) else putLong(REFUSED, version) }
        if (installed) channels.onGranted()
        installed
    }

    private companion object {
        const val TAG = "channels"
        const val APK_ASSET = "companion/tvprovider.apk"
        const val VERSION_ASSET = "companion/tvprovider.version"
        const val ANSWER_CHARS = 200
        const val PREFS = "tvprovider_companion"

        /** The carried version pm refused as an update. */
        const val REFUSED = "refused_version"
    }
}

/**
 * Whether to install the [carried] companion: where there is no provider at all, or where this
 * companion is the provider at an older version ([installed]), unless pm already refused that one
 * as an update ([refused]); never over another app's provider. An update brings what a newer
 * companion does: from version 2, being visible to apps on Android 11 ([companionInstallCommand]).
 */
internal fun companionWanted(provider: String?, installed: () -> Long?, carried: Long?, refused: Long?): Boolean {
    if (carried == null) return false
    if (provider == null) return true
    if (provider != AndroidTvChannels.COMPANION || refused == carried) return false
    return (installed() ?: return false) < carried
}

/** The installed companion's version code; null without one. */
internal fun companionVersion(pm: PackageManager): Long? = try {
    PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(AndroidTvChannels.COMPANION, 0))
} catch (_: PackageManager.NameNotFoundException) {
    null
}

/** What the companion's provider answers with the end of its log, under the same key (`CompanionTvProvider.call`). */
internal const val COMPANION_LOG = "lumbina_log"

/**
 * `pm` reading an APK of [size] bytes from the connection, over what is there and keeping its
 * rows. From Android 11 an app that targets it sees no other app's provider unless it names it in
 * its manifest; the system's TV provider is visible to all (`forceQueryable`, for system packages
 * only), and so is the companion with `--force-queryable`, which older `pm` doesn't know.
 */
internal fun companionInstallCommand(size: Int, sdk: Int): String {
    val visible = if (sdk >= Build.VERSION_CODES.R) " --force-queryable" else ""
    return "cmd package install -r$visible -S $size"
}
