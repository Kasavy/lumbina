package dev.lumbina.tv.platform.wallpaper

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.DailyPhoto
import dev.lumbina.tv.core.domain.backdrop.PhotosOfTheDay
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettings
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.network.HttpGet
import dev.lumbina.tv.core.testing.FakeSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalThenOnlinePhotosTest {

    private val context = RuntimeEnvironment.getApplication()
    private val own = "own picture".toByteArray()
    private val requests = mutableListOf<String>()

    private fun photos(
        settings: WallpaperSettings,
        online: Boolean,
        ownPictures: Boolean = true,
        onlinePhotos: Boolean = true,
        picsum: Boolean = true,
    ): LocalThenOnlinePhotos {
        if (ownPictures) File(context.filesDir, "photos").apply { mkdirs() }.resolve("1-cat.jpg").writeBytes(own)
        val http = HttpGet { url ->
            requests += url
            if (online && (picsum || "picsum" !in url)) "online".toByteArray() else throw IOException("offline")
        }
        return LocalThenOnlinePhotos(
            AppPhotoFolder(context),
            BundledPhotos(context),
            PhotosOfTheDay { listOf(DailyPhoto("https://www.bing.com/a.jpg", "A lake (© Someone)")) },
            FakeSettings(settings),
            FakeSettings(SleepSettings(onlinePhotos = onlinePhotos)),
            http,
            LumbinaLog { _, _ -> },
        )
    }

    @Test
    fun `offline, the user's own pictures show even when they aren't the wallpaper`() = runBlocking {
        val photos = photos(WallpaperSettings(source = WallpaperSource.Gradient), online = false)
        assertArrayEquals(own, photos.next(1920, 1080).bytes)
    }

    @Test
    fun `offline and without pictures of one's own, the bundled ones take turns`() = runBlocking {
        val photos = photos(WallpaperSettings(), online = false, ownPictures = false)
        val shown = List(4) { photos.next(1920, 1080).bytes }
        assertTrue(shown.all { it != null && it.isNotEmpty() })
        assertFalse(shown[0].contentEquals(shown[1]))
        assertTrue(shown[0].contentEquals(shown[3]))
    }

    @Test
    fun `where picsum refuses, a photo of the day comes with its credit and own pictures have none`() = runBlocking {
        val online = photos(WallpaperSettings(), online = true, ownPictures = false, picsum = false).next(1920, 1080)
        assertEquals("A lake (© Someone)", online.credit)
        val ours = photos(WallpaperSettings(source = WallpaperSource.MyMedia), online = true).next(1920, 1080)
        assertEquals(null, ours.credit)
    }

    @Test
    fun `with online photos off nothing is asked of the network`() = runBlocking {
        val photos = photos(WallpaperSettings(), online = true, onlinePhotos = false)
        assertArrayEquals(own, photos.next(1920, 1080).bytes)
        assertEquals(emptyList<String>(), requests)
    }
}
