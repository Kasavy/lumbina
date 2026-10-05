package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CurrentWallpaperTest {

    private val repository = FakeSettings(WallpaperSettings())
    private val settings = repository.current
    private val image = { name: String -> WallpaperItem(name, WallpaperKind.Image) }
    private val plugins = object : WallpaperPlugins {
        override fun installed() = listOf(WallpaperPluginInfo("p/s", "Plugin"))

        override suspend fun wallpapers(id: String) = listOf(image("a"), image("b"))

        override fun openSettings(id: String) = false
    }

    private val wallpaper = CurrentWallpaper(
        settings = repository,
        media = { emptyList() },
        photos = { listOf(DailyPhoto("https://photo/1", "A lake (© Someone)")) },
        plugins = plugins,
    )

    @Test
    fun `the gradient has no item`() = runTest {
        assertNull(wallpaper.items().first())
    }

    @Test
    fun `a plugin's wallpapers take turns`() = runTest {
        settings.value = WallpaperSettings(source = WallpaperSource.Plugin("p/s"), changeMinutes = 1)
        assertEquals(listOf("a", "b", "a"), wallpaper.items().take(3).toList().map { it?.uri })
    }

    @Test
    fun `photos of the day are images with their credit`() = runTest {
        settings.value = WallpaperSettings(source = WallpaperSource.PhotoOfTheDay)
        assertEquals(image("https://photo/1").copy(credit = "A lake (© Someone)"), wallpaper.items().first())
    }

    @Test
    fun `an empty folder falls back to the gradient`() = runTest {
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia)
        assertNull(wallpaper.items().first())
    }
}
