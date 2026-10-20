package dev.lumbina.tv.core.domain.sleep

import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.sleep.ScreensaverSource.OwnFiles
import dev.lumbina.tv.core.domain.sleep.ScreensaverSource.PhotosOfTheDay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreensaverSourcesTest {

    @Test
    fun `my photos are tried once, not again as the last resort`() {
        assertEquals(listOf(OwnFiles(false)), screensaverSources(ScreensaverPhotos.MyMedia, WallpaperSource.Gradient))
    }

    @Test
    fun `a plugin gives way to photos of the day, then to my photos`() {
        assertEquals(
            listOf(ScreensaverSource.Plugin("p"), PhotosOfTheDay, OwnFiles(false)),
            screensaverSources(ScreensaverPhotos.Plugin("p"), WallpaperSource.MyMedia),
        )
    }

    @Test
    fun `as on the wallpaper follows its source, the gradient meaning photos of the day`() {
        val asWallpaper = { source: WallpaperSource -> screensaverSources(ScreensaverPhotos.AsWallpaper, source) }
        assertEquals(listOf(OwnFiles(true), OwnFiles(false)), asWallpaper(WallpaperSource.MyMedia))
        assertEquals(listOf(PhotosOfTheDay, OwnFiles(false)), asWallpaper(WallpaperSource.Gradient))
        assertEquals(
            listOf(ScreensaverSource.Plugin("p"), PhotosOfTheDay, OwnFiles(false)),
            asWallpaper(WallpaperSource.Plugin("p")),
        )
    }
}
