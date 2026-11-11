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
 *
 * The turn counts on the clock, not while the home screen is watched: the item on screen and when it
 * came are kept in the settings ([WallpaperSettings.shown]), so coming back from an app or the
 * screensaver goes on from it, or to the next one if its time is up meanwhile. Only one on, however
 * long it was away: rounds counted on the clock could come back to the very same picture.
 */
class CurrentWallpaper(
    private val settings: WallpaperSettingsRepository,
    private val library: MediaLibrary,
    private val marks: MediaMarksRepository,
    private val photos: PhotosOfTheDay,
    private val plugins: WallpaperPlugins,
    /** Epoch milliseconds. */
    private val now: () -> Long,
) {
    @Inject constructor(
        settings: WallpaperSettingsRepository,
        library: MediaLibrary,
        marks: MediaMarksRepository,
        photos: PhotosOfTheDay,
        plugins: WallpaperPlugins,
    ) : this(settings, library, marks, photos, plugins, System::currentTimeMillis)

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

    /** What to show and for how long. */
    private data class Turn(val index: Int, val millis: Long)

    private fun cycle(current: Cycle, place: Place): Flow<WallpaperItem?> = flow {
        if (place.source != current.source) {
            place.source = current.source
            place.shown = null
            place.index = 0
        }
        val period = current.changeMinutes * MILLIS_PER_MINUTE
        var restarted = true
        while (true) {
            val items = load(current.source)
            if (items.isEmpty()) {
                place.shown = null
                emit(null)
                delay(RETRY_MILLIS)
                continue
            }
            val turn = next(items, place, period, restarted)
            restarted = false
            place.shown = items[turn.index]
            place.index = turn.index
            // The same item again (a single one) is the same value: the screen keeps it as it is.
            emit(items[turn.index])
            delay(turn.millis)
        }
    }

    /**
     * What to show of [items]: the file asked for, the one on screen while its time lasts ([restarted]:
     * the cycle came back, from the settings or from the home screen being away), or the one after it.
     */
    private suspend fun next(items: List<WallpaperItem>, place: Place, period: Long, restarted: Boolean): Turn {
        val saved = settings.settings.first()
        saved.startWith?.let { asked ->
            val index = items.indexOfFirst { it.uri == asked }
            settings.update { it.copy(startWith = null) }
            if (index >= 0) return shown(items, index, period)
        }
        // Back on the home screen the place is new: the one the settings kept, if it is still listed.
        val on = place.shown ?: items.firstOrNull { it.uri == saved.shown }
        val at = items.indexOf(on)
        val elapsed = now() - saved.shownAt
        val left = when {
            saved.shown != on?.uri -> period

            // The clock set back since, or it ran ahead at a boot until the network set it: the turn is over.
            elapsed < 0 -> 0

            else -> period - elapsed
        }
        return when {
            at >= 0 && restarted && left > 0 -> Turn(at, left)

            at >= 0 -> shown(items, (at + 1) % items.size, period)

            // Gone from the list (deleted, taken out): the one that took its place.
            else -> shown(items, place.index % items.size, period)
        }
    }

    /**
     * [index] comes on screen now, for a whole turn, the same one again too (one chosen that was on
     * screen): kept in the settings for the next time the cycle starts. A single item has no turns
     * to keep, and is written once.
     */
    private suspend fun shown(items: List<WallpaperItem>, index: Int, period: Long): Turn {
        val uri = items[index].uri
        settings.update { if (items.size == 1 && it.shown == uri) it else it.copy(shown = uri, shownAt = now()) }
        return Turn(index, period)
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
