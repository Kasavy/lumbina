package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.testing.FakeMediaLibrary
import dev.lumbina.tv.core.testing.FakeMediaLibrary.Companion.media
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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

    private val library = FakeMediaLibrary()
    private val marks = FakeSettings(MediaMarks())

    private val wallpaper = CurrentWallpaper(
        settings = repository,
        library = library,
        marks = marks,
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

    @Test
    fun `my media are the files with the wallpaper mark`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.mp4", MediaKind.Video), media("3.jpg"))
        marks.current.value = MediaMarks(offWallpaper = setOf("3.jpg"), unplayable = setOf("2.mp4"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 1)
        val shown = wallpaper.items().take(2).toList().map { it?.uri }
        assertEquals(listOf("file:///photos/1.jpg", "file:///photos/1.jpg"), shown)
    }

    @Test
    fun `a file chosen in the library comes first`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"), media("3.jpg"))
        val chosen = "file:///photos/2.jpg"
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 1, startWith = chosen)
        assertEquals(listOf("2.jpg", "3.jpg", "1.jpg"), wallpaper.items().take(3).toList().map { it?.title })
    }

    @Test
    fun `the file asked for is shown once, a later change of the files goes on from the one on screen`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"), media("3.jpg"))
        settings.value = WallpaperSettings(
            source = WallpaperSource.MyMedia,
            changeMinutes = 1,
            startWith = "file:///photos/1.jpg",
        )
        val shown = collect()
        advanceTimeBy(MINUTE + 1)
        changeFiles()
        advanceTimeBy(MINUTE + 1)
        // 1 asked for, 2 after a minute, kept on the change, then 3; never back to 1.
        assertEquals(listOf("1.jpg", "2.jpg", "2.jpg", "3.jpg"), shown.map { it?.title })
        assertNull(settings.value.startWith)
    }

    @Test
    fun `the file on screen deleted, the one in its place, now`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"), media("3.jpg"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 1)
        val shown = collect()
        advanceTimeBy(MINUTE + 1)
        library.files = listOf(media("1.jpg"), media("3.jpg"))
        changeFiles()
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg"), shown.map { it?.title })
    }

    private fun TestScope.collect(): List<WallpaperItem?> {
        val shown = mutableListOf<WallpaperItem?>()
        backgroundScope.launch { wallpaper.items().collect { shown += it } }
        runCurrent()
        return shown
    }

    private fun TestScope.changeFiles() {
        settings.value = settings.value.copy(mediaChangedAt = testScheduler.currentTime)
        runCurrent()
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
