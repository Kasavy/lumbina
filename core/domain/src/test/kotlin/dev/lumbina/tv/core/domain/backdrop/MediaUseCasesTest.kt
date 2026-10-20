package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.testing.FakeMediaLibrary
import dev.lumbina.tv.core.testing.FakeMediaLibrary.Companion.banner
import dev.lumbina.tv.core.testing.FakeMediaLibrary.Companion.media
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaUseCasesTest {

    private val library = FakeMediaLibrary()
    private val marks = FakeSettings(MediaMarks())
    private val appearance = FakeSettings(HomeAppearance())
    private val wallpaper = FakeSettings(WallpaperSettings())

    @Test
    fun `files are on both until taken out, and an unplayable video on neither`() {
        val marked = MediaMarks(offWallpaper = setOf("a"), unplayable = setOf("v"))
        assertFalse(marked.onWallpaper("a"))
        assertTrue(marked.onScreensaver("a"))
        assertTrue(marked.onWallpaper("b"))
        assertFalse(marked.onScreensaver("v"))
        assertEquals(MediaMarks(), marked.withWallpaper("a", on = true).without("v"))
    }

    @Test
    fun `putting an unplayable video back on gives it another try`() {
        val marked = MediaMarks(offWallpaper = setOf("v"), unplayable = setOf("v"))
        assertTrue(marked.withScreensaver("v", on = true).onScreensaver("v"))
        assertFalse(marked.withScreensaver("v", on = true).onWallpaper("v"))
        assertEquals(setOf("v"), marked.withScreensaver("v", on = false).unplayable)
    }

    @Test
    fun `unused are files with no mark and banners on no tile`() = runTest {
        library.files = listOf(media("both"), media("none"), media("saver"))
        library.banners = listOf(banner("shown"), banner("spare"))
        marks.current.value = MediaMarks(offWallpaper = setOf("none", "saver"), offScreensaver = setOf("none"))
        appearance.current.value = HomeAppearance(customBanners = mapOf("app" to banner("shown").uri))
        val delete = DeleteUnusedMedia(library, marks, appearance)

        assertEquals(listOf("none", "spare"), delete.unused())
        assertEquals(2, delete())
        assertEquals(listOf("both", "saver"), library.files.map { it.id })
        assertEquals(listOf("shown"), library.banners.map { it.id })
        assertEquals(MediaMarks(offWallpaper = setOf("saver")), marks.current.value)
    }

    @Test
    fun `deleting a banner puts its tiles back to the app's own`() = runTest {
        library.banners = listOf(banner("b"))
        val banners = mapOf("app" to banner("b").uri, "other" to "content://x")
        appearance.current.value = HomeAppearance(customBanners = banners)

        DeleteMedia(library, marks, appearance, wallpaper)(banner("b"), now = 7)

        assertTrue(library.banners.isEmpty())
        assertEquals(mapOf("other" to "content://x"), appearance.current.value.customBanners)
        assertEquals(7, wallpaper.current.value.mediaChangedAt)
    }

    @Test
    fun `banners kept among the files move to their own folder, the tiles with them`() = runTest {
        library.files = listOf(media("photo"), media("used.jpg"))
        val used = media("used.jpg").uri
        appearance.current.value = HomeAppearance(customBanners = mapOf("a" to used, "b" to used))

        SeparateBanners(library, appearance)()

        assertEquals(listOf("photo"), library.files.map { it.id })
        val moved = banner("used.jpg").uri
        assertEquals(mapOf("a" to moved, "b" to moved), appearance.current.value.customBanners)
        // Once moved, nothing more to do.
        SeparateBanners(library, appearance)()
        assertEquals(listOf("used.jpg"), library.banners.map { it.id })
    }

    @Test
    fun `a video that would not play is marked, and the wallpaper goes on`() = runTest {
        val video = media("v.mp4", MediaKind.Video)
        library.files = listOf(video)
        val mark = MarkUnplayable(library, marks, wallpaper)

        mark(video.uri, now = 5)
        // Not one of the user's files: nothing to mark.
        mark("https://plugin/clip.mp4", now = 6)

        assertEquals(setOf("v.mp4"), marks.current.value.unplayable)
        assertEquals(5, wallpaper.current.value.mediaChangedAt)
    }
}
