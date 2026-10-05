package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.app.AppSystemActions
import dev.lumbina.tv.core.domain.app.AppUsage
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.RecentAppsRepository
import dev.lumbina.tv.core.domain.app.mostUsed
import dev.lumbina.tv.core.domain.app.recentlyInstalled
import dev.lumbina.tv.core.domain.app.recentlyOpened
import dev.lumbina.tv.core.domain.backdrop.MediaFolder
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperKind
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.home.AppsOrder
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.HomeContent
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.createFolderWith
import dev.lumbina.tv.core.domain.home.withBanner
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home and "All apps" with what their rows need; [usage] is openings per package lately. */
data class HomeUiState(
    val content: HomeContent,
    val layout: HomeLayout,
    val appearance: HomeAppearance,
    val recent: List<App> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val frequent: List<App> = emptyList(),
    val opened: List<App> = emptyList(),
    val usage: Map<String, Long> = emptyMap(),
    /** Whether apps can be called by voice here: the device has a remote Lumbina hears. */
    val voice: Boolean = false,
)

private const val FREQUENT_MAX = 6
private const val HINT_MILLIS = 8_000L

/** Everything the home screen's menus and dialogs can do. */
@Suppress("TooManyFunctions") // one per menu entry
interface HomeCommands {
    val hasDetails: Boolean

    fun open(app: App)

    fun addTo(sectionId: String, app: App)

    fun addToNewCategory(name: String, app: App)

    fun removeFrom(sectionId: String, app: App)

    fun moveItem(sectionId: String, key: String, by: Int)

    fun createFolder(sectionId: String, name: String, app: App)

    fun addToFolder(folderId: String, app: App)

    fun removeFromFolder(folderId: String, app: App)

    fun renameFolder(folderId: String, name: String)

    fun dissolveFolder(folderId: String)

    fun rename(app: App, label: String)

    /** A name to call [app] by in voice commands; blank takes it away. */
    fun voiceName(app: App, name: String)

    fun hide(app: App)

    /** False when the device has no "App info" screen. */
    fun showDetails(app: App): Boolean

    /** False when the device has no uninstaller. */
    fun uninstall(app: App): Boolean

    fun unhide(app: App)

    /** The user's own pictures that can become an app's banner. */
    suspend fun bannerChoices(): List<WallpaperItem>

    /** Shows [uri] as the app's banner; null brings back the app's own. */
    fun setBanner(app: App, uri: String?)
}

@Suppress("TooManyFunctions") // implements HomeCommands
@HiltViewModel
class HomeViewModel @Inject constructor(
    installedApps: InstalledApps,
    private val appearance: HomeAppearanceRepository,
    private val layouts: HomeLayoutRepository,
    private val launcher: AppLauncher,
    private val system: AppSystemActions,
    private val media: MediaFolder,
    usage: AppUsage,
    recentApps: RecentAppsRepository,
    profile: DeviceProfile,
) : ViewModel(),
    HomeCommands {

    /** Null until apps and the layout are loaded. */
    val state: StateFlow<HomeUiState?> =
        combine(
            installedApps.observe(),
            layouts.layout,
            appearance.settings,
            usage.recent(),
            recentApps.recent,
        ) { apps, layout, look, used, opened ->
            val content = layout.arrange(apps, Locale.getDefault())
            val rows = look.rows(layout.categories.map { it.id }).filter { it.visible }.map { it.row }
            HomeUiState(
                content = content,
                layout = layout,
                appearance = look,
                recent = recentlyInstalled(content.apps, System.currentTimeMillis()),
                rows = rows,
                frequent = mostUsed(content.apps, used, FREQUENT_MAX, except = layout.favorites.apps.toSet()),
                opened = recentlyOpened(content.apps, opened),
                usage = used,
                voice = profile.hasRemoteMic,
            )
        }
            // Arranging sorts every app by name: off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, WhileUiSubscribed, null)

    private val hint = MutableStateFlow(false)
    private var hintJob: Job? = null

    /** True while the "hold OK" hint is on screen. */
    val holdOkHint: StateFlow<Boolean> = hint.asStateFlow()

    /** Home was opened: the first few times, until the user has held OK on a tile, a hint says how. */
    fun onHomeShown() {
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            var shown = false
            appearance.update { look ->
                shown = look.holdOkHintsLeft > 0
                if (shown) look.copy(holdOkHintsLeft = look.holdOkHintsLeft - 1) else look
            }
            hint.value = shown
            if (shown) {
                delay(HINT_MILLIS)
                hint.value = false
            }
        }
    }

    /** A tile's menu was opened by holding OK: the hint has done its job. */
    fun onMenuOpened() {
        hintJob?.cancel()
        hint.value = false
        viewModelScope.launch {
            appearance.update { if (it.holdOkHintsLeft > 0) it.copy(holdOkHintsLeft = 0) else it }
        }
    }

    /** Sorts "All apps" by use or by name, remembered. */
    fun setAppsOrder(order: AppsOrder) {
        viewModelScope.launch { appearance.update { it.copy(appsOrder = order) } }
    }

    override val hasDetails: Boolean get() = system.hasDetails

    override fun open(app: App) {
        launcher.launch(app.id)
    }

    override fun addTo(sectionId: String, app: App) = edit { it.add(sectionId, app.id) }

    override fun addToNewCategory(name: String, app: App) = edit {
        val id = newId()
        it.addCategory(id, name).add(id, app.id)
    }

    override fun removeFrom(sectionId: String, app: App) = edit { it.remove(sectionId, app.id) }

    override fun moveItem(sectionId: String, key: String, by: Int) = edit { it.moveItem(sectionId, key, by) }

    /** Puts a tile being moved back where it started ([Moving.from]). */
    fun putBack(tile: Moving) {
        val now = state.value?.indexOf(tile) ?: return
        if (now >= 0 && tile.from >= 0 && now != tile.from) moveItem(tile.sectionId, tile.key, tile.from - now)
    }

    override fun createFolder(sectionId: String, name: String, app: App) =
        edit { it.createFolderWith(sectionId, newId(), name, app.id) }

    override fun addToFolder(folderId: String, app: App) = edit { it.addToFolder(folderId, app.id) }

    override fun removeFromFolder(folderId: String, app: App) = edit { it.removeFromFolder(folderId, app.id) }

    override fun renameFolder(folderId: String, name: String) = edit { it.renameFolder(folderId, name) }

    override fun dissolveFolder(folderId: String) = edit { it.dissolveFolder(folderId) }

    override fun rename(app: App, label: String) = edit { it.rename(app.id, label) }

    override fun voiceName(app: App, name: String) = edit { it.voiceName(app.id, name) }

    override fun hide(app: App) = edit { it.hide(app.id) }

    override fun unhide(app: App) = edit { it.unhide(app.id) }

    override fun showDetails(app: App): Boolean = system.showDetails(app.id.packageName)

    override fun uninstall(app: App): Boolean = system.uninstall(app.id.packageName)

    override suspend fun bannerChoices(): List<WallpaperItem> =
        withContext(Dispatchers.IO) { media.items().filter { it.kind == WallpaperKind.Image } }

    override fun setBanner(app: App, uri: String?) {
        viewModelScope.launch { appearance.update { it.withBanner(app.id.flatten(), uri) } }
    }

    private fun edit(transform: (HomeLayout) -> HomeLayout) {
        viewModelScope.launch { layouts.update(transform) }
    }

    private fun newId() = UUID.randomUUID().toString()
}
