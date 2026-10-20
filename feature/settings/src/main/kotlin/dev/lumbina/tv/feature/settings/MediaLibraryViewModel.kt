package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.backdrop.DeleteMedia
import dev.lumbina.tv.core.domain.backdrop.DeleteUnusedMedia
import dev.lumbina.tv.core.domain.backdrop.MakeBanner
import dev.lumbina.tv.core.domain.backdrop.MediaFile
import dev.lumbina.tv.core.domain.backdrop.MediaLibrary
import dev.lumbina.tv.core.domain.backdrop.MediaMarks
import dev.lumbina.tv.core.domain.backdrop.MediaMarksRepository
import dev.lumbina.tv.core.domain.backdrop.ShowOnWallpaper
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The two marks a file can have. */
enum class MediaMark { Wallpaper, Screensaver }

/** What the library shows: everything or a part of it. */
enum class MediaFilter { All, Wallpaper, Screensaver, Banners, Nowhere }

/**
 * One file with its marks, or a [banner] with [shownOn], the apps whose tiles show it. A video that
 * won't play is on neither, as the wallpaper and screensaver skip it: putting it on gives it another try.
 */
data class MediaEntry(
    val file: MediaFile,
    val onWallpaper: Boolean,
    val onScreensaver: Boolean,
    val unplayable: Boolean,
    val banner: Boolean = false,
    val shownOn: List<String> = emptyList(),
) {
    val nowhere: Boolean get() = !onWallpaper && !onScreensaver

    fun has(mark: MediaMark) = if (mark == MediaMark.Wallpaper) onWallpaper else onScreensaver
}

data class MediaLibraryState(val files: List<MediaEntry>, val banners: List<MediaEntry>) {
    /** Files with no mark and banners no tile shows: what "Delete unused" takes. */
    val unused: Int get() = files.count { it.nowhere } + banners.count { it.shownOn.isEmpty() }

    fun count(filter: MediaFilter): Int = when (filter) {
        MediaFilter.All -> files.size + banners.size
        MediaFilter.Wallpaper -> files.count { it.onWallpaper }
        MediaFilter.Screensaver -> files.count { it.onScreensaver }
        MediaFilter.Banners -> banners.size
        MediaFilter.Nowhere -> unused
    }
}

/** What the library screen can do. */
interface MediaCommands {
    fun mark(entry: MediaEntry, mark: MediaMark, on: Boolean)
    fun markAll(mark: MediaMark, on: Boolean)
    fun showNow(entry: MediaEntry)
    fun toBanner(entry: MediaEntry)
    fun delete(entry: MediaEntry)
    fun deleteUnused()
}

@HiltViewModel
class MediaLibraryViewModel @Inject constructor(
    private val library: MediaLibrary,
    private val marks: MediaMarksRepository,
    private val wallpaper: WallpaperSettingsRepository,
    private val deleteMedia: DeleteMedia,
    private val deleteUnusedMedia: DeleteUnusedMedia,
    private val showOnWallpaper: ShowOnWallpaper,
    private val makeBanner: MakeBanner,
    appearance: HomeAppearanceRepository,
    apps: InstalledApps,
) : ViewModel(),
    MediaCommands {

    /** Null until the folders are first read. */
    val state: StateFlow<MediaLibraryState?> =
        combine(listings(), marks.settings, appearance.settings, apps.observe()) { listed, marked, look, installed ->
            val (files, banners) = listed
            val names = installed.associate { it.id.flatten() to it.label }
            // Per banner, the apps whose tiles show it; a package's name where the app is gone.
            val tiles = look.customBanners.entries.groupBy({ it.value }) { (key, _) ->
                names[key] ?: key.substringBefore('/')
            }
            MediaLibraryState(
                files = files.map { it.entry(marked) },
                banners = banners.map { it.entry(marked).copy(banner = true, shownOn = tiles[it.uri].orEmpty()) },
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, WhileUiSubscribed, null)

    /**
     * The folders, read again every couple of seconds: files come in through the phone page's
     * server in the remote's service, which tells this screen nothing. Only while it is open.
     */
    private fun listings(): Flow<Pair<List<MediaFile>, List<MediaFile>>> = flow {
        while (true) {
            emit(library.files() to library.banners())
            delay(POLL_MILLIS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun mark(entry: MediaEntry, mark: MediaMark, on: Boolean) {
        val id = entry.file.id
        changeMarks(mark) { if (mark == MediaMark.Wallpaper) it.withWallpaper(id, on) else it.withScreensaver(id, on) }
    }

    override fun markAll(mark: MediaMark, on: Boolean) {
        val ids = state.value?.files.orEmpty().map { it.file.id }
        changeMarks(mark) { start ->
            ids.fold(start) { marked, id ->
                if (mark == MediaMark.Wallpaper) marked.withWallpaper(id, on) else marked.withScreensaver(id, on)
            }
        }
    }

    private fun changeMarks(mark: MediaMark, transform: (MediaMarks) -> MediaMarks) {
        viewModelScope.launch {
            marks.update(transform)
            // The wallpaper reads its files each round: a file taken out or put back counts at once.
            if (mark == MediaMark.Wallpaper) wallpaper.update { it.copy(mediaChangedAt = System.currentTimeMillis()) }
        }
    }

    override fun showNow(entry: MediaEntry) {
        viewModelScope.launch { showOnWallpaper(entry.file, now = System.currentTimeMillis()) }
    }

    override fun toBanner(entry: MediaEntry) {
        viewModelScope.launch(Dispatchers.IO) { makeBanner(entry.file, now = System.currentTimeMillis()) }
    }

    override fun delete(entry: MediaEntry) {
        viewModelScope.launch(Dispatchers.IO) { deleteMedia(entry.file, now = System.currentTimeMillis()) }
    }

    override fun deleteUnused() {
        viewModelScope.launch(Dispatchers.IO) {
            deleteUnusedMedia()
            wallpaper.update { it.copy(mediaChangedAt = System.currentTimeMillis()) }
        }
    }

    private fun MediaFile.entry(marked: MediaMarks) = MediaEntry(
        file = this,
        onWallpaper = marked.onWallpaper(id),
        onScreensaver = marked.onScreensaver(id),
        unplayable = id in marked.unplayable,
    )

    private companion object {
        const val POLL_MILLIS = 2_000L
    }
}
