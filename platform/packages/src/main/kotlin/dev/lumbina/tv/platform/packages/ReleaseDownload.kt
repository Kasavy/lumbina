package dev.lumbina.tv.platform.packages

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.update.Release
import dev.lumbina.tv.core.domain.update.publishedSum
import dev.lumbina.tv.core.network.HttpGet
import dev.lumbina.tv.core.network.HttpStatusException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Brings a release's package onto the device and checks it against the checksum published beside
 * it. What fails on the way through the network is tried again ([retrying]), and a download cut
 * short goes on from where it stopped; a package already here and whole is not downloaded again.
 */
internal class ReleaseDownload @Inject constructor(private val http: HttpGet, private val log: LumbinaLog) {

    /**
     * True when [file] holds the package [release] published, downloaded now or before; false when
     * its checksum is not published or what came differs. Throws an [IOException] when the network
     * fails for good, or at once when another try would not help ([FinalAnswer], a 4xx).
     */
    suspend fun fetch(release: Release, file: File, progress: suspend (Float) -> Unit): Boolean {
        // Read first: a release whose sum is missing is not worth the download.
        val published = publishedSum(retried("checksums") { http.text(release.sumsUrl) }, release.apkName)
        if (published == null) {
            log.log(UPDATE_TAG, "${release.apkName}: no published sum")
            return false
        }
        if (isWhole(file, release) && sha256(file) == published) {
            log.log(UPDATE_TAG, "${release.apkName}: already downloaded")
            return true
        }
        // Whole but not the published one, or longer: nothing to go on from.
        if (file.exists() && (release.bytes <= 0 || file.length() >= release.bytes)) file.delete()
        var shown = 0f
        retried(release.apkName) {
            // A try that starts over does not take the shown progress back.
            download(release, file) { fraction ->
                if (fraction - shown >= PROGRESS_STEP || (fraction == 1f && shown < 1f)) {
                    shown = fraction
                    progress(fraction)
                }
            }
        }
        val sum = sha256(file)
        val fits = sum == published
        val verdict = if (fits) "" else ": they differ"
        log.log(UPDATE_TAG, "${release.apkName}: sum $sum, published $published$verdict")
        return fits
    }

    private fun isWhole(file: File, release: Release) = file.exists() && file.length() == release.bytes

    private suspend fun <T> retried(what: String, block: suspend () -> T): T =
        retrying(RETRY_PAUSES, { log.log(UPDATE_TAG, "$what: $it, trying again") }, block)

    /**
     * Streams the package into [file], reporting the fraction done. What [file] already holds of it
     * (a download cut short) is asked for no more: the server sends the rest, or the whole again.
     */
    private suspend fun download(release: Release, file: File, progress: suspend (Float) -> Unit) {
        val start = if (release.bytes > 0 && file.exists()) file.length() else 0L
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true
        if (start > 0) connection.setRequestProperty("Range", "bytes=$start-")
        try {
            val code = connection.responseCode
            val resumed = code == HttpURLConnection.HTTP_PARTIAL
            if (code == RANGE_NOT_SATISFIABLE) {
                // What is here is no beginning of the package: the next try starts over.
                file.delete()
                throw IOException("HTTP $code for the rest from $start")
            }
            if (code != HttpURLConnection.HTTP_OK && !resumed) throw HttpStatusException(code, release.apkUrl)
            if (start > 0) {
                val how = if (resumed) "goes on from $start bytes" else "starts over, the server sends it whole"
                log.log(UPDATE_TAG, "${release.apkName}: $how")
            }
            val from = if (resumed) start else 0L
            connection.inputStream.use { input -> copy(input, file, from, release.bytes, progress) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Writes [input] to [file] after its first [done] bytes, reporting the fraction of [published]
     * done. More than that is no package of the release: given up.
     */
    private suspend fun copy(
        input: InputStream,
        file: File,
        done: Long,
        published: Long,
        progress: suspend (Float) -> Unit,
    ) {
        var written = done
        writing { FileOutputStream(file, done > 0) }.use { output ->
            input.chunks { buffer, read ->
                writing { output.write(buffer, 0, read) }
                written += read
                if (published > 0 && written > published) throw FinalAnswer("more than the published $published bytes")
                if (published > 0) progress((written.toFloat() / published).coerceAtMost(1f))
            }
        }
    }

    /** [block] on the package's file; its failure (no room) is a [FinalAnswer]: another try would not get further. */
    private inline fun <T> writing(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw FinalAnswer("the package could not be written: $e")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> input.chunks { buffer, read -> digest.update(buffer, 0, read) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Hands what [this] gives to [block] a buffer at a time, until it ends. */
    private inline fun InputStream.chunks(block: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            block(buffer, read)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000
        const val BUFFER = 64 * 1024
        const val PROGRESS_STEP = 0.01f
        const val RANGE_NOT_SATISFIABLE = 416

        /** Pauses before the second and the third try. */
        val RETRY_PAUSES = listOf(2_000L, 5_000L)
    }
}
