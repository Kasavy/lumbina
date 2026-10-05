package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.testing.FakeSettings
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteMyMediaTest {

    private var cleared = false
    private val folder = object : PhotoFolder {
        override fun count() = 0

        override fun add(name: String, input: InputStream, size: Long) = false

        override fun contains(uri: String) = uri.startsWith("file:///photos/")

        override fun clear() {
            cleared = true
        }
    }
    private val appearance = FakeSettings(HomeAppearance())
    private val look = appearance.current
    private val settings = FakeSettings(WallpaperSettings())
    private val wallpaper = settings.current
    private val delete = DeleteMyMedia(folder, appearance, settings)

    @Test
    fun `clears the folder, drops banners made from it and leaves my media`() = runTest {
        look.value = HomeAppearance(customBanners = mapOf("a" to "file:///photos/1.jpg", "b" to "content://other"))
        wallpaper.value = WallpaperSettings(source = WallpaperSource.MyMedia)

        delete(now = 42)

        assertTrue(cleared)
        assertEquals(mapOf("b" to "content://other"), look.value.customBanners)
        assertEquals(WallpaperSettings(source = WallpaperSource.Gradient, mediaChangedAt = 42), wallpaper.value)
    }

    @Test
    fun `another wallpaper source stays`() = runTest {
        wallpaper.value = WallpaperSettings(source = WallpaperSource.PhotoOfTheDay)
        delete(now = 1)
        assertEquals(WallpaperSource.PhotoOfTheDay, wallpaper.value.source)
    }
}
