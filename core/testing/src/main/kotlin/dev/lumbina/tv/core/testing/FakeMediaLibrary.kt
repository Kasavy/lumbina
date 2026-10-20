package dev.lumbina.tv.core.testing

import dev.lumbina.tv.core.domain.backdrop.MediaAdd
import dev.lumbina.tv.core.domain.backdrop.MediaFile
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.domain.backdrop.MediaLibrary
import dev.lumbina.tv.core.domain.backdrop.MediaPurpose
import java.io.InputStream

/** The two folders in memory: [files] and [banners], newest first, ids as their file names. */
class FakeMediaLibrary(var files: List<MediaFile> = emptyList(), var banners: List<MediaFile> = emptyList()) :
    MediaLibrary {

    override fun files() = files

    override fun banners() = banners

    override fun add(name: String, input: InputStream, size: Long, purpose: MediaPurpose): MediaAdd {
        val file = if (purpose == MediaPurpose.Banner) banner(name) else media(name)
        if (purpose == MediaPurpose.Banner) banners = listOf(file) + banners else files = listOf(file) + files
        return MediaAdd.Added(file)
    }

    override fun room() = Long.MAX_VALUE

    override fun delete(ids: Collection<String>) {
        files = files.filter { it.id !in ids }
        banners = banners.filter { it.id !in ids }
    }

    override fun toBanner(id: String): String? {
        val file = files.firstOrNull { it.id == id && it.kind == MediaKind.Photo } ?: return null
        files = files - file
        val moved = banner(id)
        banners = listOf(moved) + banners
        return moved.uri
    }

    override fun copyToBanner(id: String): String? {
        files.firstOrNull { it.id == id && it.kind == MediaKind.Photo } ?: return null
        val copy = banner("copy-$id")
        banners = listOf(copy) + banners
        return copy.uri
    }

    companion object {
        fun media(id: String, kind: MediaKind = MediaKind.Photo) = MediaFile(id, "file:///photos/$id", kind, id, 0)

        fun banner(id: String) = MediaFile(id, "file:///banners/$id", MediaKind.Photo, id, 0)
    }
}
