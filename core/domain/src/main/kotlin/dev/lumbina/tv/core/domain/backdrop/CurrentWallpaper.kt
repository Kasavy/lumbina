package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.MILLIS_PER_MINUTE
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The wallpaper to show now, following the settings: null for the gradient, otherwise items of
 * the chosen source in turn, a new one every [WallpaperSettings.changeMinutes]. The list is
 * fetched again each round, so new photos or plugin items show up. A change of the files
 * ([WallpaperSettings.mediaChangedAt]) keeps the one on screen if it is still there, and goes to the
 * one in its place if it is gone; [WallpaperSettings.startWith] is shown once, then forgotten.
 */
class CurrentWallpaper @Inject constructor(
    private val settings: WallpaperSettingsRepository,
    private val library: MediaLibrary,
    private val marks: MediaMarksRepository,
    private val photos: PhotosOfTheDay,
    private val plugins: WallpaperPlugins,
) {
    /** Restarts only on what changes the cycle, not on e.g. [WallpaperSettings.dimAtNight]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun items(): Flow<WallpaperItem?> = flow {
        // Across restarts of the cycle: where it is.
        val place = Place()
        emitAll(
            settings.settings
                .map { Cycle(it.source, it.changeMinutes, it.mediaChangedAt) }
                .distinctUntilChanged()
                .flatMapLatest { current ->
                    if (current.source == WallpaperSource.Gradient) flowOf(null) else cycle(current, place)
                },
        )
    }

    private data class Cycle(val source: WallpaperSource, val changeMinutes: Int, val mediaChangedAt: Long)

    private class Place {
        var source: WallpaperSource? = null
        var shown: WallpaperItem? = null
        var index = 0
    }

    private fun cycle(current: Cycle, place: Place): Flow<WallpaperItem?> = flow {
        if (place.source != current.source) {
            place.source = current.source
            place.shown = null
            place.index = 0
        }
        var restarted = true
        while (true) {
            val items = load(current.source)
            if (items.isEmpty()) {
                place.shown = null
                emit(null)
                delay(RETRY_MILLIS)
                continue
            }
            val index = next(items, place, keep = restarted)
            restarted = false
            place.shown = items[index]
            place.index = index
            emit(items[index])
            // A single video or a still source with one item stays until the settings change.
            delay(current.changeMinutes * MILLIS_PER_MINUTE)
        }
    }

    /** What to show of [items]: the file asked for, the one on screen ([keep]) or the one after it. */
    private suspend fun next(items: List<WallpaperItem>, place: Place, keep: Boolean): Int {
        val asked = settings.settings.first().startWith
        if (asked != null) {
            settings.update { it.copy(startWith = null) }
            items.indexOfFirst { it.uri == asked }.takeIf { it >= 0 }?.let { return it }
        }
        val at = items.indexOf(place.shown)
        return when {
            at >= 0 && keep -> at

            at >= 0 -> (at + 1) % items.size

            // Gone from the list (deleted, taken out): the one that took its place.
            else -> place.index % items.size
        }
    }

    private suspend fun load(source: WallpaperSource): List<WallpaperItem> = try {
        when (source) {
            WallpaperSource.Gradient -> emptyList()

            WallpaperSource.MyMedia -> {
                val marked = marks.settings.first()
                library.files().filter { marked.onWallpaper(it.id) }.map { it.toWallpaperItem() }
            }

            WallpaperSource.PhotoOfTheDay -> photos.photos().map {
                WallpaperItem(it.url, WallpaperKind.Image, credit = it.credit)
            }

            is WallpaperSource.Plugin -> plugins.wallpapers(source.id)
        }
    } catch (_: IOException) {
        emptyList()
    }

    private companion object {
        const val RETRY_MILLIS = 5 * MILLIS_PER_MINUTE
    }
}
