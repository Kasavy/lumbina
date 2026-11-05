package dev.lumbina.tv.platform.wallpaper

import dev.lumbina.tv.core.domain.backdrop.MediaAdd
import dev.lumbina.tv.core.domain.backdrop.MediaPurpose
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppMediaLibraryTest {

    private val context = RuntimeEnvironment.getApplication()
    private val library = AppMediaLibrary(context)

    private fun add(name: String, bytes: ByteArray = ByteArray(8), purpose: MediaPurpose = MediaPurpose.Backdrop) =
        library.add(name, bytes.inputStream(), bytes.size.toLong(), purpose)

    @Test
    fun `a photo copied into the banners stays a photo, and deleting it leaves the copy`() {
        val bytes = ByteArray(16) { it.toByte() }
        val added = library.add("sea.jpg", bytes.inputStream(), bytes.size.toLong(), MediaPurpose.Backdrop)
        val photo = (added as MediaAdd.Added).file
        val copy = library.copyToBanner(photo.id)
        assertEquals(listOf(photo.id), library.files().map { it.id })
        val banner = library.banners().single()
        assertEquals(copy, banner.uri)
        assertNotEquals(photo.id, banner.id)
        library.delete(listOf(photo.id))
        assertEquals(listOf(banner.id), library.banners().map { it.id })
    }

    @Test
    fun `the same photo picked for a banner again, the copy made before`() {
        val photo = (add("sea.jpg") as MediaAdd.Added).file
        val first = library.copyToBanner(photo.id)
        assertEquals(first, library.copyToBanner(photo.id))
        assertEquals(1, library.banners().size)
    }

    @Test
    fun `banners are not among the files, and a gif is neither`() {
        add("b.png", purpose = MediaPurpose.Banner)
        assertEquals(MediaAdd.NotMedia, add("v.gif"))
        assertEquals(MediaAdd.NotMedia, add("clip.mp4", purpose = MediaPurpose.Banner))
        assertEquals(listOf("b.png"), library.banners().map { it.title })
        assertEquals(emptyList<String>(), library.files().map { it.title })
    }

    @Test
    fun `a name keeps to its last part and a little punctuation, short enough for the disk`() {
        val file = (add("../../shared_prefs/x:y?.jpg") as MediaAdd.Added).file
        assertEquals("xy.jpg", file.title)
        val long = (add("Я".repeat(300) + ".jpeg") as MediaAdd.Added).file
        assertTrue(long.title, long.title.length <= 80 && long.title.endsWith(".jpeg"))
    }

    @Test
    fun `a file cut short is not kept, nor its part`() {
        val cut = object : InputStream() {
            private var left = 4
            override fun read(): Int = if (left-- > 0) 1 else throw IOException("dropped")
        }
        assertEquals(MediaAdd.Incomplete, library.add("sea.jpg", cut, 8, MediaPurpose.Backdrop))
        assertEquals(emptyList<String>(), File(context.filesDir, "photos").list()?.toList().orEmpty())
    }

    @Test
    fun `more than there is room for is refused before anything is written`() {
        assertEquals(
            MediaAdd.NoRoom,
            library.add("big.mp4", ByteArray(0).inputStream(), Long.MAX_VALUE / 2, MediaPurpose.Backdrop),
        )
    }

    /** A photo made a banner (moved there), its id. */
    private fun banner(name: String = "sea.jpg"): String {
        val photo = (add(name) as MediaAdd.Added).file
        library.toBanner(photo.id)
        return library.banners().single().id
    }

    @Test
    fun `a banner no tile shows goes back to the photos as it is`() {
        val id = banner()
        assertEquals(id, library.fromBanner(id, keep = false))
        assertEquals(listOf(id), library.files().map { it.id })
        assertTrue(library.banners().isEmpty())
    }

    @Test
    fun `a banner the tiles show goes as a copy, once however often it is sent`() {
        val id = banner()
        val copy = library.fromBanner(id, keep = true)
        assertNotEquals(id, copy)
        assertEquals(listOf(id), library.banners().map { it.id })
        assertEquals(copy, library.fromBanner(id, keep = true))
        assertEquals(listOf(copy), library.files().map { it.id })
    }

    @Test
    fun `a banner sent before as a copy, sent once more without its tile, leaves only that copy`() {
        val id = banner()
        val copy = library.fromBanner(id, keep = true)
        assertEquals(copy, library.fromBanner(id, keep = false))
        assertEquals(listOf(copy), library.files().map { it.id })
        assertTrue(library.banners().isEmpty())
    }

    @Test
    fun `a photo of the same name is never replaced by a banner coming back`() {
        val id = banner()
        File(context.filesDir, "photos/$id").writeBytes(ByteArray(3))
        val back = library.fromBanner(id, keep = false)
        assertNotEquals(id, back)
        assertEquals(3L, File(context.filesDir, "photos/$id").length())
        assertEquals(setOf(id, back), library.files().map { it.id }.toSet())
        assertTrue(library.banners().isEmpty())
    }

    @Test
    fun `nothing comes back for a name that is no banner here`() {
        assertNull(library.fromBanner("missing.jpg", keep = false))
        assertNull(library.fromBanner("../photos/x.jpg", keep = true))
    }

    @Test
    fun `nothing is copied for a name that is no photo here`() {
        assertNull(library.copyToBanner("missing.jpg"))
        assertNull(library.copyToBanner("../photos/x.jpg"))
    }
}
