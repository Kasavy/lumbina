package dev.lumbina.tv.platform.packages

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAdb
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import dev.lumbina.tv.core.domain.update.AppUpdates
import dev.lumbina.tv.core.domain.update.Release
import dev.lumbina.tv.core.domain.update.ShellInstall
import dev.lumbina.tv.core.domain.update.UpdateCheck
import dev.lumbina.tv.core.domain.update.UpdateStep
import dev.lumbina.tv.core.domain.update.isNewer
import dev.lumbina.tv.core.domain.update.publishedSum
import dev.lumbina.tv.core.domain.update.shellInstallAnswer
import dev.lumbina.tv.core.domain.update.shellInstallCommand
import dev.lumbina.tv.core.domain.update.shellInstallResult
import dev.lumbina.tv.core.network.HttpGet
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

/**
 * Lumbina's releases on GitHub, read without signing in: the list is public, and 60 requests an
 * hour from one address do for checks made by hand (an address many share may run out, and the
 * check fails for a while). The package is checked against the checksum published beside it, and
 * that it is Lumbina's, no older than this build. Then, where Lumbina has its own connection to the device's
 * debugging, it installs the package over that with nothing asked: the user agreed to that
 * connection, and a firmware's own question may be in a language they can't read. Elsewhere, or
 * when that way fails, the package goes to the system's installer, which asks the user.
 */
internal class GitHubUpdates @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpGet,
    private val system: SystemInfo,
    private val log: LumbinaLog,
    private val profile: DeviceProfile,
    private val adb: LocalAdb,
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
        // Left by an install that went through: this process was replaced before it could clean up.
        withContext(Dispatchers.IO) { packageFile().delete() }
        val latest = latest() ?: return UpdateCheck.Failed
        log.log(TAG, "latest ${latest.version}, here ${system.appVersion}")
        return if (isNewer(latest.version, system.appVersion)) UpdateCheck.Available(latest) else UpdateCheck.UpToDate
    }

    /** The latest release, if it could be read and has its package and checksums where they may come from. */
    private suspend fun latest(): Release? {
        val published = try {
            json.decodeFromString<GitHubRelease>(http.text(LATEST))
        } catch (_: IOException) {
            null
        } catch (e: SerializationException) {
            log.log(TAG, "the latest release could not be read: $e")
            null
        } ?: return null
        return published.toRelease()?.takeIf { trusted(it.apkUrl) && trusted(it.sumsUrl) }
            ?: null.also { log.log(TAG, "${published.tag}: no package or checksums, or not over https") }
    }

    override fun install(release: Release): Flow<UpdateStep> = flow {
        val file = packageFile()
        try {
            emit(UpdateStep.Downloading(0f))
            // Read first: a release whose sum is missing is not worth the download.
            val published = publishedSum(http.text(release.sumsUrl), release.apkName)
            val sum = published?.let { download(release, file) { emit(UpdateStep.Downloading(it)) } }
            if (published == null || published != sum) {
                log.log(TAG, "${release.apkName}: sum $sum, published $published")
                emit(UpdateStep.Corrupt)
                return@flow
            }
            unfit(file)?.let {
                emit(it)
                return@flow
            }
            if (profile.localAdb && adb.state() == AdbState.Connected) {
                emit(UpdateStep.InstallingItself)
                val answer = installOverAdb(file)
                if (answer != ShellInstall.Failed) {
                    afterAdb(answer)?.let { emit(it) }
                    return@flow
                }
            }
            emit(UpdateStep.Installing)
            val status = installer.install(file)
            log.log(TAG, "installer: $status")
            emit(
                when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateStep.Cancelled

                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                    PackageInstaller.STATUS_FAILURE_CONFLICT,
                    -> UpdateStep.Refused

                    else -> UpdateStep.NotInstalled
                },
            )
        } catch (e: IOException) {
            log.log(TAG, "${release.apkName}: $e")
            emit(UpdateStep.Failed)
        } finally {
            file.delete()
        }
    }.flowOn(Dispatchers.IO)

    private fun packageFile() = File(context.cacheDir, "update").apply { mkdirs() }.resolve("lumbina.apk")

    /** Sends [file] to the package manager through Lumbina's own adb, as `adb install` would. */
    private suspend fun installOverAdb(file: File): ShellInstall {
        // What to start once replaced: Lumbina's own launcher activity, whatever the app names it.
        val start = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
            ?: return ShellInstall.Failed
        val command = shellInstallCommand(file.length(), start.flattenToShortString())
        val answer = file.inputStream().use { adb.exec(command, it) }
        return shellInstallResult(answer).also {
            log.log(TAG, "over adb: $it ${answer?.let(::shellInstallAnswer)?.take(ANSWER_CHARS)}")
        }
    }

    /** What to say after an install over adb that did not fail; null when nothing is left to say. */
    private fun afterAdb(answer: ShellInstall): UpdateStep? = when (answer) {
        // Normally never seen: the install stops this process first.
        ShellInstall.Done, ShellInstall.Failed -> null

        ShellInstall.Refused -> UpdateStep.Refused

        // The device may still finish it, and then Lumbina restarts; if not, OK tries again.
        ShellInstall.Pending -> UpdateStep.NotInstalled
    }

    /**
     * Why [file] is not to be installed, or null when it is: it must be Lumbina's own package, no
     * older than this build. Over adb nothing else would stop another app from going in unasked.
     */
    private fun unfit(file: File): UpdateStep? {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(file.path, 0)
        if (archive?.packageName != context.packageName) {
            log.log(TAG, "the package is ${archive?.packageName}, not Lumbina's")
            return UpdateStep.Corrupt
        }
        val here = PackageInfoCompat.getLongVersionCode(manager.getPackageInfo(context.packageName, 0))
        val there = PackageInfoCompat.getLongVersionCode(archive)
        if (there < here) {
            log.log(TAG, "the package's version code $there is lower than this build's $here")
            return UpdateStep.Older
        }
        return null
    }

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

    /** Streams the package into [file], reporting the fraction done; returns its SHA-256. */
    private suspend fun download(release: Release, file: File, progress: suspend (Float) -> Unit): String {
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.bytes
            connection.inputStream.use { input ->
                file.outputStream().use { output -> copy(input, output, digest, total, release.bytes, progress) }
            }
        } finally {
            connection.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Copies [input] to [output], adding what passes to [digest] and reporting the fraction of [total]
     * done. More than the release said it has ([published], when known) is no package of it: given up.
     */
    @Suppress("LongParameterList") // the stream's ends, its sum, two sizes and the report
    private suspend fun copy(
        input: InputStream,
        output: OutputStream,
        digest: MessageDigest,
        total: Long,
        published: Long,
        progress: suspend (Float) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER)
        var done = 0L
        var reported = 0f
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            done += read
            if (published > 0 && done > published) throw IOException("more than the published $published bytes")
            val fraction = if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else 0f
            if (fraction - reported >= PROGRESS_STEP) {
                reported = fraction
                progress(fraction)
            }
        }
    }

    private companion object {
        const val LATEST = BuildConfig.UPDATES_LATEST
        const val TAG = "update"
        const val TIMEOUT_MILLIS = 20_000
        const val BUFFER = 64 * 1024
        const val PROGRESS_STEP = 0.01f
        const val ANSWER_CHARS = 200
    }
}
