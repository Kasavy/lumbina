package dev.lumbina.tv.core.domain.backdrop

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The wallpaper to show now, following the settings: null for the gradient, otherwise items of
 * the chosen source in turn, a new one every [WallpaperSettings.changeMinutes]. The list is
 * fetched again each round, so new photos or plugin items show up.
 */
class CurrentWallpaper @Inject constructor(
    private val settings: WallpaperSettingsRepository,
    private val media: MediaFolder,
    private val photos: PhotosOfTheDay,
    private val plugins: WallpaperPlugins,
) {
    /** Restarts only on what changes the cycle, not on e.g. [WallpaperSettings.dimAtNight]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun items(): Flow<WallpaperItem?> = settings.settings
        .map { Cycle(it.source, it.changeMinutes, it.mediaChangedAt) }
        .distinctUntilChanged()
        .flatMapLatest { current -> if (current.source == WallpaperSource.Gradient) flowOf(null) else cycle(current) }

    private data class Cycle(val source: WallpaperSource, val changeMinutes: Int, val mediaChangedAt: Long)

    private fun cycle(current: Cycle): Flow<WallpaperItem?> = flow {
        var shown: WallpaperItem? = null
        while (true) {
            val items = load(current.source)
            if (items.isEmpty()) {
                emit(null)
                delay(RETRY_MILLIS)
                continue
            }
            // Next after the one on screen, so a refreshed list doesn't restart from the first.
            val next = items[(items.indexOf(shown) + 1) % items.size]
            shown = next
            emit(next)
            // A single video or a still source with one item stays until the settings change.
            delay(current.changeMinutes * MILLIS_PER_MINUTE)
        }
    }

    private suspend fun load(source: WallpaperSource): List<WallpaperItem> = try {
        when (source) {
            WallpaperSource.Gradient -> emptyList()

            WallpaperSource.MyMedia -> media.items()

            WallpaperSource.PhotoOfTheDay -> photos.photos(SCREEN_WIDTH).map {
                WallpaperItem(it.url, WallpaperKind.Image, credit = it.credit)
            }

            is WallpaperSource.Plugin -> plugins.wallpapers(source.id)
        }
    } catch (_: IOException) {
        emptyList()
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val SCREEN_WIDTH = 1920
        const val RETRY_MILLIS = 5 * 60_000L
    }
}
