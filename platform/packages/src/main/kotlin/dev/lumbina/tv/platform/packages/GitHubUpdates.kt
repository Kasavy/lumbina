package dev.lumbina.tv.platform.packages

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import dev.lumbina.tv.core.domain.update.AppUpdates
import dev.lumbina.tv.core.domain.update.Release
import dev.lumbina.tv.core.domain.update.ShellInstall
import dev.lumbina.tv.core.domain.update.UpdateCheck
import dev.lumbina.tv.core.domain.update.UpdateStep
import dev.lumbina.tv.core.domain.update.isNewer
import dev.lumbina.tv.core.network.HttpGet
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A release as GitHub's API describes it; only what is read. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tag: String = "",
    val assets: List<Asset> = emptyList(),
) {
    @Serializable
    data class Asset(
        val name: String = "",
        val size: Long = 0,
        @SerialName("browser_download_url") val url: String = "",
    )

    /**
     * The package and the checksums among the release's files; null for a release without either:
     * nothing would say the package is the published one.
     */
    fun toRelease(): Release? {
        val apk = assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        val sums = assets.firstOrNull { it.name in SUMS_NAMES } ?: return null
        return Release(tag.removePrefix("v"), apk.name, apk.url, apk.size, sums.url)
    }
}

/**
 * `sha256sum`'s output under the names it is published as; a signature beside it is not it. Not in
 * [GitHubRelease]'s companion: a private one hides the serializer the plugin puts there.
 */
private val SUMS_NAMES = setOf("SHA256SUMS", "SHA256SUMS.txt")

/** The log's tag for everything of updating Lumbina. */
internal const val UPDATE_TAG = "update"

/**
 * Lumbina's releases on GitHub, read without signing in: the list is public, and 60 requests an
 * hour from one address do for checks made by hand (an address many share may run out, and the
 * check fails for a while). The package is downloaded and checked against the checksum published
 * beside it ([ReleaseDownload]), and that it is Lumbina's, no older than this build ([PackageCheck]).
 * Then, where Lumbina has its own connection to the device's debugging, it installs the package
 * over that ([AdbSelfInstall]); elsewhere, or when that way fails, the system's installer asks the
 * user ([SystemInstaller]), after the user let Lumbina install apps where it may not yet.
 */
@Suppress("LongParameterList") // the device, the release's server and the steps of an install
internal class GitHubUpdates @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpGet,
    private val system: SystemInfo,
    private val log: LumbinaLog,
    private val downloads: ReleaseDownload,
    private val checks: PackageCheck,
    private val overAdb: AdbSelfInstall,
    private val installer: SystemInstaller,
) : AppUpdates {

    private val json = Json { ignoreUnknownKeys = true }

    /** Only where the manifest asks to install packages: an edition for a store does not. */
    override val supported: Boolean by lazy {
        val info: PackageInfo? = try {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        info?.requestedPermissions?.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES) == true
    }

    override suspend fun check(): UpdateCheck {
        val latest = latest()
        // Left by an install that went through (this process was replaced before it could clean up),
        // cut short, or of a release since replaced (another download would go on from its end): only
        // a whole package of the latest release, newer than this build, is kept for the next try.
        withContext(Dispatchers.IO) {
            packageFile().takeUnless {
                checks.isNewerRelease(it) && (latest == null || checks.isVersion(it, latest.version))
            }?.delete()
        }
        latest ?: return UpdateCheck.Failed
        log.log(UPDATE_TAG, "latest ${latest.version}, here ${system.appVersion}")
        return if (isNewer(latest.version, system.appVersion)) UpdateCheck.Available(latest) else UpdateCheck.UpToDate
    }

    /** The latest release, if it could be read and has its package and checksums where they may come from. */
    private suspend fun latest(): Release? {
        val published = try {
            json.decodeFromString<GitHubRelease>(http.text(LATEST))
        } catch (_: IOException) {
            null
        } catch (e: SerializationException) {
            log.log(UPDATE_TAG, "the latest release could not be read: $e")
            null
        } ?: return null
        return published.toRelease()?.takeIf { trusted(it.apkUrl) && trusted(it.sumsUrl) }
            ?: null.also { log.log(UPDATE_TAG, "${published.tag}: no package or checksums, or not over https") }
    }

    private val installing = UpdateInstall(
        object : InstallSteps {
            override suspend fun overAdb() = overAdb.available()

            override fun mayInstallApps() = installer.mayInstallApps()

            override fun canAskToInstallApps() = installer.canAskToInstallApps()

            override fun askToInstallApps() = installer.askToInstallApps()

            override suspend fun fetch(release: Release, file: File, progress: suspend (Float) -> Unit) =
                downloads.fetch(release, file, progress)

            override fun unfit(file: File) = checks.unfit(file)

            override suspend fun installOverAdb(file: File) = overAdb.install(file)

            override fun stepAfter(answer: ShellInstall) = overAdb.stepAfter(answer)

            override suspend fun awaitInstallable(asked: Boolean) = installer.awaitInstallable(asked)

            override suspend fun installThroughSystem(file: File) = installer.install(file)
        },
        log,
    )

    override fun install(release: Release): Flow<UpdateStep> =
        installing.run(release, packageFile()).flowOn(Dispatchers.IO)

    override fun askToInstallApps() {
        installer.askToInstallApps()
    }

    private fun packageFile() = File(context.cacheDir, "update").apply { mkdirs() }.resolve("lumbina.apk")

    /**
     * Whether a release's file may come from [url]: over https; over http only from a build that
     * checks a test server over http itself, and from that server.
     */
    private fun trusted(url: String): Boolean = try {
        val address = URL(url)
        val latest = URL(LATEST)
        address.protocol == "https" ||
            (address.protocol == "http" && latest.protocol == "http" && address.host == latest.host)
    } catch (_: MalformedURLException) {
        false
    }

    private companion object {
        const val LATEST = BuildConfig.UPDATES_LATEST
    }
}
