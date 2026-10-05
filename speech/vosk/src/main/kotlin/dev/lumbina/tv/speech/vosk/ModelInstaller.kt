package dev.lumbina.tv.speech.vosk

import dev.lumbina.tv.core.domain.speech.ModelState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class InstallFailure(val reason: ModelState.Reason, cause: Throwable? = null) :
    Exception(reason.name, cause)

/**
 * Downloads a model archive (resuming a partial one), checks its size and SHA-256, unpacks it
 * next to the models and swaps it in with one rename, so a half-installed model is never
 * loaded. Blocking I/O: call on an I/O dispatcher. Cancellation leaves the partial download for
 * the next attempt.
 */
internal class ModelInstaller @Inject constructor(private val store: VoskModelStore, private val source: ModelSource) {

    /** @throws InstallFailure */
    suspend fun install(model: VoskModel, progress: (ModelState) -> Unit) {
        val archive = download(model, progress)
        progress(ModelState.Unpacking)
        try {
            unpack(archive, model.language)
        } catch (e: IOException) {
            throw InstallFailure(ModelState.Reason.Storage, e)
        }
        archive.delete()
    }

    private suspend fun download(model: VoskModel, progress: (ModelState) -> Unit): File {
        store.downloads.mkdirs()
        val archive = File(store.downloads, "${model.name}.zip")
        if (archive.length() > model.sizeBytes) archive.delete()
        if (store.root.usableSpace < model.sizeBytes * SPACE_FACTOR - archive.length()) {
            throw InstallFailure(ModelState.Reason.NoSpace)
        }
        if (archive.length() < model.sizeBytes) fetch(model, archive, progress)
        if (archive.length() != model.sizeBytes || sha256(archive) != model.sha256) {
            archive.delete()
            throw InstallFailure(ModelState.Reason.Corrupt)
        }
        return archive
    }

    /**
     * Tries each mirror in turn, each one continuing where the previous stopped: some networks
     * stall connections to some hosts after the first few kilobytes.
     */
    private suspend fun fetch(model: VoskModel, archive: File, progress: (ModelState) -> Unit) {
        var failure: InstallFailure? = null
        for (url in model.urls) {
            try {
                fetchFrom(url, model, archive, progress)
                return
            } catch (e: InstallFailure) {
                if (e.reason != ModelState.Reason.Network) throw e
                failure = e
            }
        }
        throw checkNotNull(failure)
    }

    private suspend fun fetchFrom(url: String, model: VoskModel, archive: File, progress: (ModelState) -> Unit) {
        progress(ModelState.Downloading(archive.length(), model.sizeBytes))
        val opened = try {
            source.open(url, archive.length())
        } catch (e: IOException) {
            throw InstallFailure(ModelState.Reason.Network, e)
        }
        opened.use {
            FileOutputStream(archive, opened.offset > 0).use { out ->
                copy(opened, out) { bytes -> progress(ModelState.Downloading(bytes, model.sizeBytes)) }
            }
        }
    }

    /** Reports progress about once per percent. */
    private suspend fun copy(opened: Opened, out: FileOutputStream, progress: (Long) -> Unit) {
        val buffer = ByteArray(BUFFER_BYTES)
        var bytes = opened.offset
        var reported = -1L
        var read = readOrFail(opened, buffer)
        while (read >= 0) {
            currentCoroutineContext().ensureActive()
            try {
                out.write(buffer, 0, read)
            } catch (e: IOException) {
                throw InstallFailure(ModelState.Reason.Storage, e)
            }
            bytes += read
            if (bytes - reported >= REPORT_EVERY_BYTES) {
                progress(bytes)
                reported = bytes
            }
            read = readOrFail(opened, buffer)
        }
        progress(bytes)
    }

    private fun readOrFail(opened: Opened, buffer: ByteArray): Int = try {
        opened.stream.read(buffer)
    } catch (e: IOException) {
        throw InstallFailure(ModelState.Reason.Network, e)
    }

    private fun unpack(archive: File, language: String) {
        val staging = store.staging(language).apply { deleteRecursively() }
        val stagingPath = staging.canonicalPath + File.separator
        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                // Archives hold one top-level folder (vosk-model-small-ru-0.22/…): drop it.
                val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
                val target = File(staging, relative)
                when {
                    relative.isEmpty() -> Unit

                    !target.canonicalPath.startsWith(stagingPath) -> throw InstallFailure(ModelState.Reason.Corrupt)

                    entry.isDirectory -> target.mkdirs()

                    else -> {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
                    }
                }
            }
        }
        swapIn(staging, language)
    }

    /** Replaces the installed model with [staging] if it is a complete model. */
    private fun swapIn(staging: File, language: String) {
        if (!store.isModel(staging)) {
            staging.deleteRecursively()
            throw InstallFailure(ModelState.Reason.Corrupt)
        }
        val target = store.directory(language)
        target.deleteRecursively()
        if (!staging.renameTo(target)) throw InstallFailure(ModelState.Reason.Storage)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val REPORT_EVERY_BYTES = 512 * 1024L

        /** Archive plus unpacked model (roughly twice the archive). */
        const val SPACE_FACTOR = 3
    }
}
