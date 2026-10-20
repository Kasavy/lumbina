package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Deletes what nothing uses: files with neither the wallpaper nor the screensaver mark, and
 * banners no app tile shows. Returns how many files went. Blocking file work: call it off the
 * main thread.
 */
class DeleteUnusedMedia @Inject constructor(
    private val library: MediaLibrary,
    private val marks: MediaMarksRepository,
    private val appearance: HomeAppearanceRepository,
) {
    /** The ids [invoke] would delete now. */
    suspend fun unused(): List<String> {
        val marked = marks.settings.first()
        val shown = appearance.settings.first().customBanners.values.toSet()
        val files = library.files().filter { !marked.onWallpaper(it.id) && !marked.onScreensaver(it.id) }
        val banners = library.banners().filter { it.uri !in shown }
        return (files + banners).map { it.id }
    }

    suspend operator fun invoke(): Int {
        val ids = unused()
        library.delete(ids)
        val left = (library.files() + library.banners()).map { it.id }.toSet()
        marks.update { it.keepOnly(left) }
        return ids.size
    }
}

/**
 * Deletes one file or banner: a tile showing it as its banner goes back to the app's own, and its
 * marks are dropped. Blocking file work: call it off the main thread.
 */
class DeleteMedia @Inject constructor(
    private val library: MediaLibrary,
    private val marks: MediaMarksRepository,
    private val appearance: HomeAppearanceRepository,
    private val settings: WallpaperSettingsRepository,
) {
    suspend operator fun invoke(file: MediaFile, now: Long) {
        library.delete(listOf(file.id))
        appearance.update { look -> look.copy(customBanners = look.customBanners.filterValues { it != file.uri }) }
        marks.update { it.without(file.id) }
        // The wallpaper may be showing it: the one after it, now.
        settings.update { it.copy(mediaChangedAt = now) }
    }
}

/** Puts a file on the wallpaper now: the wallpaper shows the user's files, from this one on. */
class ShowOnWallpaper @Inject constructor(
    private val marks: MediaMarksRepository,
    private val settings: WallpaperSettingsRepository,
) {
    suspend operator fun invoke(file: MediaFile, now: Long) {
        marks.update { it.withWallpaper(file.id, on = true) }
        settings.update { it.copy(source = WallpaperSource.MyMedia, startWith = file.uri, mediaChangedAt = now) }
    }
}

/**
 * Moves a photo to the banners: it leaves the wallpaper and screensaver, for app tiles
 * only. Blocking file work: call it off the main thread.
 */
class MakeBanner @Inject constructor(
    private val library: MediaLibrary,
    private val marks: MediaMarksRepository,
    private val settings: WallpaperSettingsRepository,
) {
    suspend operator fun invoke(file: MediaFile, now: Long) {
        library.toBanner(file.id) ?: return
        marks.update { it.without(file.id) }
        settings.update { it.copy(mediaChangedAt = now) }
    }
}

/**
 * Banners kept among the wallpaper's files, from before banners had a folder of their own, move
 * there: they leave the wallpaper and screensaver, and the tiles follow them to their new place.
 * Does nothing once they have moved. Blocking file work: call it off the main thread.
 */
class SeparateBanners @Inject constructor(
    private val library: MediaLibrary,
    private val appearance: HomeAppearanceRepository,
) {
    suspend operator fun invoke() {
        val inUse = appearance.settings.first().customBanners.values.toSet()
        val moved = library.files()
            .filter { it.uri in inUse && it.kind == MediaKind.Photo }
            .mapNotNull { file -> library.toBanner(file.id)?.let { file.uri to it } }
            .toMap()
        if (moved.isEmpty()) return
        appearance.update { look ->
            look.copy(customBanners = look.customBanners.mapValues { (_, uri) -> moved[uri] ?: uri })
        }
    }
}

/**
 * A video the device failed to play gets the "won't play" mark: the wallpaper and screensaver
 * skip it from then on, and the wallpaper goes on to the next file now. Not for files that are not
 * the user's (a plugin's video).
 */
class MarkUnplayable @Inject constructor(
    private val library: MediaLibrary,
    private val marks: MediaMarksRepository,
    private val settings: WallpaperSettingsRepository,
) {
    suspend operator fun invoke(uri: String, now: Long) {
        val file = library.files().firstOrNull { it.uri == uri } ?: return
        marks.update { it.copy(unplayable = it.unplayable + file.id) }
        settings.update { it.copy(mediaChangedAt = now) }
    }
}
