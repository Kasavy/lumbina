package dev.lumbina.tv.platform.wallpaper

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.backdrop.MediaAdd
import dev.lumbina.tv.core.domain.backdrop.MediaFile
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.domain.backdrop.MediaLibrary
import dev.lumbina.tv.core.domain.backdrop.MediaPurpose
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's photos and videos in `files/photos/` and the banners in `files/banners/`, inside the
 * app (no storage permission needed). A file's id is its name there: the time it came, then the
 * name it came with.
 */
@Singleton
internal class AppMediaLibrary @Inject constructor(@ApplicationContext context: Context) : MediaLibrary {

    private val filesDir: File = context.filesDir
    private val media: File = File(filesDir, "photos")
    private val bannerDir: File = File(filesDir, "banners")

    override fun files(): List<MediaFile> = list(media, MEDIA_EXTENSIONS)

    override fun banners(): List<MediaFile> = list(bannerDir, IMAGE_EXTENSIONS)

    /** Bytes of files being written now: two phones sending at once share the room. */
    private var incoming = 0L

    override fun add(name: String, input: InputStream, size: Long, purpose: MediaPurpose): MediaAdd {
        val safe = safeName(name)
        val banner = purpose == MediaPurpose.Banner
        val dir = if (banner) bannerDir else media
        val allowed = if (banner) IMAGE_EXTENSIONS else MEDIA_EXTENSIONS
        if (File(safe).extension.lowercase() !in allowed) return MediaAdd.NotMedia
        synchronized(this) {
            if (size > room()) return MediaAdd.NoRoom
            incoming += size
        }
        return try {
            dir.mkdirs()
            write(File(dir, "${System.currentTimeMillis()}-$safe"), input, size)
        } finally {
            synchronized(this) { incoming -= size }
        }
    }

    private fun write(file: File, input: InputStream, size: Long): MediaAdd {
        // Written under a name no listing takes, and named for good once whole: the banner panel
        // reads the folder while a picture comes in and must not show half of it.
        val part = File(file.parentFile, "${file.name}$PART")
        // A dropped connection ends the stream early: keep only what arrived whole.
        val complete = runCatching { part.outputStream().use { input.copyTo(it) } }.getOrNull() == size &&
            part.renameTo(file)
        if (!complete) {
            part.delete()
            return MediaAdd.Incomplete
        }
        // A video the device can't play would only be a black wallpaper: not kept, and the phone told why.
        if (file.extension.lowercase() in VIDEO_EXTENSIONS) {
            VideoProbe.unplayable(file)?.let { what ->
                file.delete()
                return MediaAdd.Unplayable(what)
            }
        }
        return MediaAdd.Added(file.toMediaFile())
    }

    override fun room(): Long = synchronized(this) {
        (filesDir.usableSpace - RESERVE_BYTES - incoming).coerceAtLeast(0)
    }

    override fun delete(ids: Collection<String>) {
        // A bare name only: nothing outside the two folders can be reached through an id.
        for (id in ids.filter(::isName)) {
            File(media, id).delete()
            File(bannerDir, id).delete()
        }
    }

    override fun toBanner(id: String): String? {
        val file = File(media, id)
        if (!isName(id) || !file.isFile || file.extension.lowercase() !in IMAGE_EXTENSIONS) return null
        bannerDir.mkdirs()
        val moved = File(bannerDir, id)
        return if (file.renameTo(moved)) Uri.fromFile(moved).toString() else null
    }

    override fun copyToBanner(id: String): String? {
        val file = File(media, id)
        if (!isName(id) || !file.isFile || file.extension.lowercase() !in IMAGE_EXTENSIONS) return null
        // The same photo picked again: the copy made before (same name, same size), not another one.
        val title = id.substringAfter('-')
        val made = bannerDir.listFiles()?.firstOrNull {
            it.name.substringAfter('-') == title && it.length() == file.length()
        }
        if (made != null) return Uri.fromFile(made).toString()
        if (file.length() > room()) return null
        bannerDir.mkdirs()
        // A name of its own (deleting the photo deletes by name in both folders), whole or not at all.
        // Not the photo's own name either, should the copy come within its millisecond.
        val at = System.currentTimeMillis().let { if ("$it-$title" == id) it + 1 else it }
        val copy = File(bannerDir, "$at-$title")
        val part = File(bannerDir, "${copy.name}$PART")
        val done = runCatching { file.copyTo(part, overwrite = true) }.isSuccess && part.renameTo(copy)
        if (!done) part.delete()
        return if (done) Uri.fromFile(copy).toString() else null
    }

    private fun isName(id: String) = id.isNotEmpty() && '/' !in id && '\\' !in id && !id.startsWith(".")

    // Newest first: names start with the time they were added.
    private fun list(dir: File, extensions: Set<String>): List<MediaFile> = dir.listFiles { f ->
        f.isFile && f.extension.lowercase() in extensions
    }?.sortedByDescending { it.name }.orEmpty().map { it.toMediaFile() }

    private fun File.toMediaFile() = MediaFile(
        id = name,
        uri = Uri.fromFile(this).toString(),
        kind = if (extension.lowercase() in VIDEO_EXTENSIONS) MediaKind.Video else MediaKind.Photo,
        title = name.substringAfter('-'),
        addedAt = name.substringBefore('-').toLongOrNull() ?: lastModified(),
    )

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "m4v")
        val MEDIA_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS
        const val PART = ".part"

        /** What sent files must leave free: the device needs room for its own updates and apps. */
        const val RESERVE_BYTES = 500L * 1024 * 1024
    }
}

/**
 * The name a file came with, kept to letters, digits and a little punctuation, and short enough
 * for the file system (255 bytes, a Cyrillic letter taking two), its extension kept.
 */
private fun safeName(name: String): String {
    val safe = name.substringAfterLast('/').substringAfterLast('\\')
        .filter { it.isLetterOrDigit() || it in SAFE_PUNCTUATION }
    if (safe.length <= MAX_NAME) return safe
    val extension = File(safe).extension.take(MAX_NAME / 2)
    return safe.dropLast(extension.length + 1).take(MAX_NAME - extension.length - 1) + "." + extension
}

private const val SAFE_PUNCTUATION = ".-_ "
private const val MAX_NAME = 80
