package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.MILLIS_PER_MINUTE
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

    private var clock = 0L
    private val library = FakeMediaLibrary()
    private val marks = FakeSettings(MediaMarks())

    private val wallpaper = CurrentWallpaper(
        settings = repository,
        library = library,
        marks = marks,
        photos = { listOf(DailyPhoto("https://photo/1", "A lake (© Someone)")) },
        plugins = plugins,
        now = { clock },
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

    @Test
    fun `back on the home screen the turn goes on from the one on screen`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"), media("3.jpg"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 5)
        assertEquals("1.jpg", wallpaper.items().first()?.title)
        // Away for a while (an app, the screensaver): the same one while its time lasts.
        clock = 2 * MILLIS_PER_MINUTE
        assertEquals("1.jpg", wallpaper.items().first()?.title)
        // Its time ran out while away: the next one, not the first again.
        clock = 6 * MILLIS_PER_MINUTE
        assertEquals("2.jpg", wallpaper.items().first()?.title)
        clock = 12 * MILLIS_PER_MINUTE
        assertEquals("3.jpg", wallpaper.items().first()?.title)
    }

    @Test
    fun `back on the home screen only what is left of the turn is waited`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 5)
        wallpaper.items().first()
        clock = 4 * MILLIS_PER_MINUTE
        val shown = mutableListOf<String?>()
        val job = launch { wallpaper.items().collect { shown += it?.title } }
        runCurrent()
        assertEquals(listOf("1.jpg"), shown)
        advanceTimeBy(MILLIS_PER_MINUTE + 1)
        assertEquals(listOf("1.jpg", "2.jpg"), shown)
        job.cancel()
    }

    @Test
    fun `a clock set back since the one on screen came ends its turn`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 5)
        clock = 60 * MILLIS_PER_MINUTE
        assertEquals("1.jpg", wallpaper.items().first()?.title)
        // A boot ran ahead, then the network set the clock back an hour.
        clock = 1 * MILLIS_PER_MINUTE
        assertEquals("2.jpg", wallpaper.items().first()?.title)
    }

    @Test
    fun `the one on screen chosen again in the library gets a whole turn`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 5)
        assertEquals("1.jpg", wallpaper.items().first()?.title)
        clock = 4 * MILLIS_PER_MINUTE
        settings.value = settings.value.copy(startWith = "file:///photos/1.jpg")
        assertEquals("1.jpg", wallpaper.items().first()?.title)
        clock = 6 * MILLIS_PER_MINUTE
        assertEquals("1.jpg", wallpaper.items().first()?.title)
    }

    @Test
    fun `the one on screen gone while away, the first of those left`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"), media("3.jpg"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 5)
        wallpaper.items().take(2).toList()
        library.files = listOf(media("1.jpg"), media("3.jpg"))
        clock = 1 * MILLIS_PER_MINUTE
        assertEquals("1.jpg", wallpaper.items().first()?.title)
    }

    @Test
    fun `a shorter turn set while away counts from when the one on screen came`() = runTest {
        library.files = listOf(media("1.jpg"), media("2.jpg"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 60)
        assertEquals("1.jpg", wallpaper.items().first()?.title)
        clock = 10 * MILLIS_PER_MINUTE
        settings.value = settings.value.copy(changeMinutes = 5)
        assertEquals("2.jpg", wallpaper.items().first()?.title)
    }

    @Test
    fun `back after many turns away, the next one, not the one the clock would have reached`() = runTest {
        library.files = (1..5).map { media("$it.jpg") }
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 5)
        assertEquals("1.jpg", wallpaper.items().first()?.title)
        clock = 25 * MILLIS_PER_MINUTE
        assertEquals("2.jpg", wallpaper.items().first()?.title)
    }

    @Test
    fun `a single item is kept in the settings once, not at every turn`() = runTest {
        library.files = listOf(media("1.jpg"))
        settings.value = WallpaperSettings(source = WallpaperSource.MyMedia, changeMinutes = 1)
        collect()
        clock = 10 * MILLIS_PER_MINUTE
        advanceTimeBy(3 * MINUTE)
        assertEquals(0L, settings.value.shownAt)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
