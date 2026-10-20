package dev.lumbina.tv.core.domain.sleep

import dev.lumbina.tv.core.domain.backdrop.WallpaperSource

/** Where the screensaver looks for what to show. */
sealed interface ScreensaverSource {
    /** The user's files with the wallpaper's mark ([forWallpaper]) or the screensaver's. */
    data class OwnFiles(val forWallpaper: Boolean) : ScreensaverSource

    /** A wallpaper plugin's images and videos. */
    data class Plugin(val id: String) : ScreensaverSource

    /** Photos of the day: Bing's, the saved ones offline, picsum.photos where neither is there. */
    data object PhotosOfTheDay : ScreensaverSource
}

/**
 * The screensaver's sources for its [choice], in the order they are tried: the chosen one, photos
 * of the day where a plugin gives nothing, and last the user's own files with the screensaver mark
 * (offline, the choice may give nothing). "As on the wallpaper" follows [wallpaper]; the gradient
 * has no photos, so photos of the day.
 */
fun screensaverSources(choice: ScreensaverPhotos, wallpaper: WallpaperSource): List<ScreensaverSource> {
    val chosen = when (choice) {
        ScreensaverPhotos.AsWallpaper -> when (wallpaper) {
            WallpaperSource.MyMedia -> listOf(ScreensaverSource.OwnFiles(forWallpaper = true))

            is WallpaperSource.Plugin -> listOf(
                ScreensaverSource.Plugin(wallpaper.id),
                ScreensaverSource.PhotosOfTheDay,
            )

            WallpaperSource.PhotoOfTheDay, WallpaperSource.Gradient -> listOf(ScreensaverSource.PhotosOfTheDay)
        }

        ScreensaverPhotos.MyMedia -> listOf(ScreensaverSource.OwnFiles(forWallpaper = false))

        ScreensaverPhotos.PhotoOfTheDay -> listOf(ScreensaverSource.PhotosOfTheDay)

        is ScreensaverPhotos.Plugin -> listOf(ScreensaverSource.Plugin(choice.id), ScreensaverSource.PhotosOfTheDay)
    }
    return (chosen + ScreensaverSource.OwnFiles(forWallpaper = false)).distinct()
}
