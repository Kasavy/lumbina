package dev.lumbina.tv.platform.wallpaper

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.BackdropPhoto
import dev.lumbina.tv.core.domain.backdrop.DailyPhoto
import dev.lumbina.tv.core.domain.backdrop.MediaMarks
import dev.lumbina.tv.core.domain.backdrop.PhotosOfTheDay
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperKind
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettings
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.sleep.ScreensaverPhotos
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.network.HttpGet
import dev.lumbina.tv.core.testing.FakeSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScreensaverPhotoSourceTest {

    private val context = RuntimeEnvironment.getApplication()
    private val log = LumbinaLog { _, _ -> }
    private val own = "own picture".toByteArray()
    private val requests = mutableListOf<String>()
    private fun plugins(vararg given: WallpaperItem) = object : WallpaperPlugins {
        override fun installed() = emptyList<WallpaperPluginInfo>()

        override suspend fun wallpapers(id: String) = given.toList()

        override fun openSettings(id: String) = false
    }

    private fun photos(
        choice: ScreensaverPhotos,
        online: Boolean,
        wallpaper: WallpaperSettings = WallpaperSettings(),
        ownPictures: Boolean = true,
        marks: MediaMarks = MediaMarks(),
        bing: Boolean = true,
        plugins: WallpaperPlugins = plugins(),
    ): ScreensaverPhotoSource {
        if (ownPictures) File(context.filesDir, "photos").apply { mkdirs() }.resolve("1-cat.jpg").writeBytes(own)
        val http = HttpGet { url ->
            requests += url
            if (online && (bing || "bing" !in url)) url.toByteArray() else throw IOException("offline")
        }
        return ScreensaverPhotoSource(
            AppMediaLibrary(context),
            FakeSettings(marks),
            PhotosOfTheDay { listOf(DailyPhoto("https://www.bing.com/a.jpg", "A lake (© Someone)")) },
            SavedDailyPhotos(context, http, log),
            plugins,
            FakeSettings(wallpaper),
            FakeSettings(SleepSettings(screensaverPhotos = choice)),
            http,
            log,
        )
    }

    @Test
    fun `offline, the user's own photos show even when the choice was photos of the day`() = runBlocking {
        val photos = photos(ScreensaverPhotos.PhotoOfTheDay, online = false)
        assertArrayEquals(own, photos.next(1920, 1080, videos = true)!!.bytes)
    }

    @Test
    fun `offline and without photos of one's own, nothing, and the screensaver shows its gradient`() = runBlocking {
        val photos = photos(ScreensaverPhotos.AsWallpaper, online = false, ownPictures = false)
        assertNull(photos.next(1920, 1080, videos = true))
    }

    @Test
    fun `a saved photo of the day is read from the disk, not asked of the network`() = runBlocking {
        val saved = File(context.filesDir, "daily").apply { mkdirs() }.resolve("1.jpg").apply { writeBytes(own) }
        val offline = HttpGet { url -> throw IOException("offline: $url") }
        val source = ScreensaverPhotoSource(
            AppMediaLibrary(context),
            FakeSettings(MediaMarks()),
            PhotosOfTheDay { listOf(DailyPhoto(android.net.Uri.fromFile(saved).toString(), "Saved")) },
            SavedDailyPhotos(context, offline, log),
            plugins(),
            FakeSettings(WallpaperSettings()),
            FakeSettings(SleepSettings(screensaverPhotos = ScreensaverPhotos.PhotoOfTheDay)),
            offline,
            log,
        )
        val shown = source.next(1920, 1080, videos = true)!!
        assertArrayEquals(own, shown.bytes)
        assertEquals("Saved", shown.credit)
    }

    @Test
    fun `a photo of the day given by its web address but saved is read from the disk`() = runBlocking {
        val photo = DailyPhoto("https://www.bing.com/a.jpg", "A lake (© Someone)")
        SavedDailyPhotos(context, { own }, log).keep(listOf(photo))
        val photos = photos(ScreensaverPhotos.PhotoOfTheDay, online = false, ownPictures = false)
        assertArrayEquals(own, photos.next(1920, 1080, videos = true)!!.bytes)
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun `from the internet, a photo of the day comes first with its credit, picsum where Bing fails`() = runBlocking {
        val day = photos(
            ScreensaverPhotos.PhotoOfTheDay,
            online = true,
            ownPictures = false,
        ).next(1920, 1080, videos = true)!!
        assertEquals("A lake (© Someone)", day.credit)
        val photos = photos(ScreensaverPhotos.PhotoOfTheDay, online = true, ownPictures = false, bing = false)
        val picsum = photos.next(1920, 1080, videos = true)!!
        assertEquals("https://picsum.photos/1920/1080", picsum.bytes.decodeToString())
        assertNull(picsum.credit)
    }

    @Test
    fun `as the wallpaper with my media, own photos and nothing asked of the network`() = runBlocking {
        val wallpaper = WallpaperSettings(source = WallpaperSource.MyMedia)
        val photos = photos(ScreensaverPhotos.AsWallpaper, online = true, wallpaper)
        assertArrayEquals(own, photos.next(1920, 1080, videos = true)!!.bytes)
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun `my photos leave out those without the screensaver mark`() = runBlocking {
        val marks = MediaMarks(offScreensaver = setOf("1-cat.jpg"))
        val photos = photos(ScreensaverPhotos.MyMedia, online = true, marks = marks)
        // None left of one's own, and nothing asked of the network instead.
        assertNull(photos.next(1920, 1080, videos = true))
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun `a video of one's own is given to play through, not read`() = runBlocking {
        File(context.filesDir, "photos").apply { mkdirs() }.resolve("2-clip.mp4").writeBytes("video".toByteArray())
        val marks = MediaMarks(offScreensaver = setOf("1-cat.jpg"))
        val shown = photos(ScreensaverPhotos.MyMedia, online = false, marks = marks).next(1920, 1080, videos = true)!!
        assertTrue(shown.video.toString(), shown.video.orEmpty().endsWith("2-clip.mp4"))
        assertEquals(0, shown.bytes.size)
    }

    @Test
    fun `as the wallpaper with a plugin of videos, one of them is given to play through, with its title`() =
        runBlocking {
            val clip = WallpaperItem("https://example.com/aerial.mov", WallpaperKind.Video, title = "Hong Kong")
            val wallpaper = WallpaperSettings(source = WallpaperSource.Plugin("p/s"))
            val shown = photos(ScreensaverPhotos.AsWallpaper, online = true, wallpaper, plugins = plugins(clip))
                .next(1920, 1080, videos = true)!!
            assertEquals(clip.uri, shown.video)
            assertEquals("Hong Kong", shown.credit)
            assertEquals(emptyList<String>(), requests)
        }

    @Test
    fun `a plugin chosen for the screensaver itself is asked, whatever the wallpaper shows`() = runBlocking {
        val clip = WallpaperItem("https://example.com/aerial.mov", WallpaperKind.Video)
        val shown = photos(ScreensaverPhotos.Plugin("p/s"), online = true, plugins = plugins(clip))
            .next(1920, 1080, videos = true)!!
        assertEquals(clip.uri, shown.video)
    }

    @Test
    fun `without videos, a plugin that gives only videos leaves it to the photos of the day`() = runBlocking {
        val clip = WallpaperItem("https://example.com/aerial.mov", WallpaperKind.Video)
        val wallpaper = WallpaperSettings(source = WallpaperSource.Plugin("p/s"))
        val shown = photos(ScreensaverPhotos.AsWallpaper, online = true, wallpaper, plugins = plugins(clip))
            .next(1920, 1080, videos = false)!!
        assertNull(shown.video)
        assertEquals("A lake (© Someone)", shown.credit)
    }

    @Test
    fun `without videos, a video of one's own is left out`() = runBlocking {
        File(context.filesDir, "photos").apply { mkdirs() }.resolve("2-clip.mp4").writeBytes("video".toByteArray())
        val shown = photos(ScreensaverPhotos.MyMedia, online = false).next(1920, 1080, videos = false)!!
        assertArrayEquals(own, shown.bytes)
    }
}

private val BackdropPhoto.bytes: ByteArray get() = (this as? BackdropPhoto.Image)?.bytes ?: ByteArray(0)
private val BackdropPhoto.video: String? get() = (this as? BackdropPhoto.Video)?.uri
