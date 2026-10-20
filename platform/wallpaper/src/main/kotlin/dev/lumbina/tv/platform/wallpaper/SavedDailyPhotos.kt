package dev.lumbina.tv.platform.wallpaper

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.DailyPhoto
import dev.lumbina.tv.core.network.HttpGet
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The newest photos of the day, up to [KEPT], in `files/daily/` with their credits in [INDEX]:
 * what shows offline instead of a bare gradient.
 */
@Singleton
internal class SavedDailyPhotos @Inject constructor(
    @ApplicationContext context: Context,
    private val http: HttpGet,
    private val log: LumbinaLog,
) {
    private val dir = File(context.filesDir, "daily")
    private val lock = Mutex()

    /** [photo] from the disk, if it is there. */
    fun local(photo: DailyPhoto): DailyPhoto? =
        fileOf(photo.url).takeIf { it.isFile }?.let { DailyPhoto(Uri.fromFile(it).toString(), photo.credit) }

    /** The saved photos, newest first. */
    suspend fun all(): List<DailyPhoto> = lock.withLock {
        index().mapNotNull { (name, credit) ->
            File(dir, name).takeIf { it.isFile }?.let { DailyPhoto(Uri.fromFile(it).toString(), credit) }
        }
    }

    /**
     * Downloads those of [photos] (newest first) not saved yet; then only the newest [KEPT] stay.
     * One that fails to download is left for the next time.
     */
    suspend fun keep(photos: List<DailyPhoto>) {
        dir.mkdirs()
        // Without the lock: the saved photos are not kept waiting for the network.
        for (photo in photos) {
            val file = fileOf(photo.url)
            if (!file.isFile) download(photo.url, file)
        }
        lock.withLock {
            val before = index()
            val fresh = photos.map { fileOf(it.url).name to it.credit }
            val names = fresh.map { it.first }.toSet()
            val kept = (fresh + before.filter { it.first !in names })
                .filter { File(dir, it.first).isFile }
                .take(KEPT)
            writeIndex(kept)
            // Only what was saved here goes: photos listed before or just now, and downloads cut short.
            val ours = before.map { it.first }.toSet() + names
            val keptNames = kept.map { it.first }.toSet()
            dir.listFiles()
                ?.filter { (it.name in ours && it.name !in keptNames) || it.name.endsWith(PART) }
                ?.forEach { it.delete() }
        }
    }

    private suspend fun download(url: String, file: File) {
        val part = File(dir, "${file.name}$PART")
        try {
            part.writeBytes(http.bytes(url))
            if (!part.renameTo(file)) throw IOException("not renamed")
        } catch (e: IOException) {
            log.log(TAG, "photo of the day not saved: $e")
            part.delete()
        }
    }

    /** Each saved file's name and its credit (null: none), newest first. */
    private fun index(): List<Pair<String, String?>> = runCatching { File(dir, INDEX).readLines() }
        .getOrDefault(emptyList())
        .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 } }
        .map { (name, credit) -> name to credit.ifEmpty { null } }

    // Written aside and put in place whole: an index cut short would lose the photos it no longer lists.
    private fun writeIndex(kept: List<Pair<String, String?>>) {
        val written = File(dir, "$INDEX$PART")
        written.writeText(kept.joinToString("") { (name, credit) -> "$name\t${credit.orEmpty()}\n" })
        val index = File(dir, INDEX).toPath()
        try {
            Files.move(written.toPath(), index, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            log.log(TAG, "index not written: $e")
            written.delete()
        }
    }

    // A name of its own for each address: Bing's ids carry characters a file name should not.
    private fun fileOf(url: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return File(dir, digest.take(NAME_BYTES).joinToString("") { "%02x".format(Locale.ROOT, it) } + ".jpg")
    }

    private companion object {
        const val KEPT = 10
        const val INDEX = "index.txt"
        const val PART = ".part"
        const val NAME_BYTES = 10
        const val TAG = "photos"
    }
}
