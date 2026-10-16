package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
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
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.RowSpotlight
import dev.lumbina.tv.core.domain.home.createFolderWith
import dev.lumbina.tv.core.domain.home.withBanner
import dev.lumbina.tv.core.domain.phone.PhonePage
import dev.lumbina.tv.core.domain.phone.PhoneRemoteLink
import dev.lumbina.tv.core.ui.UNDO_MILLIS
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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

/** How often the banner panel looks for a picture sent from a phone. */
private const val BANNER_POLL_MILLIS = 2_000L

/** Everything the home screen's menus and dialogs can do. */
@Suppress("TooManyFunctions") // one per menu entry
interface HomeCommands {
    val hasDetails: Boolean

    fun open(app: App)

    fun addTo(sectionId: String, app: App)

    fun addToNewCategory(name: String, app: App)

    fun removeFrom(sectionId: String, app: App)

    /** As [removeFrom], said on screen and undoable for a few seconds: for the menu's own "remove" entries. */
    fun takeOut(sectionId: String, app: App)

    /** Undoes [takeOut] of [app] while that is still offered, back into its folder; otherwise [addTo]. */
    fun putBack(sectionId: String, app: App)

    fun moveItem(sectionId: String, key: String, by: Int)

    /** Takes the shortcut to all apps out of Favorites; said on screen and undoable for a few seconds. */
    fun removeShortcut()

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

    /** The user's own pictures that can become an app's banner, again whenever one comes or goes. */
    fun bannerChoices(): Flow<List<WallpaperItem>>

    /** Shows [uri] as the app's banner; null brings back the app's own. */
    fun setBanner(app: App, uri: String?)

    /** The phone remote page that pictures are sent from, or why a phone can't reach it now. */
    suspend fun phonePage(): PhonePage
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
    spotlight: RowSpotlight,
    private val phoneLink: PhoneRemoteLink,
) : ViewModel(),
    HomeCommands {

    /** The row the settings are working on now: home brings it into view and keeps it lit. */
    val spotlight: StateFlow<String?> = spotlight.key

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
    val holdOkHint: StateFlow<Boolean> get() = hintShown

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

    private val failures = Failures(viewModelScope)

    /** The app that just failed to open, for a few seconds. */
    internal val failed: StateFlow<Failed?> get() = failures.state

    override fun open(app: App) {
        if (!launcher.launch(app.id)) failures.say(Failed.App(app.label))
    }

    override fun addTo(sectionId: String, app: App) = edit { it.add(sectionId, app.id) }

    override fun addToNewCategory(name: String, app: App) = edit {
        val id = newId()
        it.addCategory(id, name).add(id, app.id)
    }

    override fun removeFrom(sectionId: String, app: App) = edit { it.remove(sectionId, app.id) }

    override fun takeOut(sectionId: String, app: App) {
        val section = state.value?.layout?.section(sectionId)
        val undo = if (section == null || section.isFavorites) {
            UndoKind.OutOfFavorites to ""
        } else {
            UndoKind.OutOfCategory to section.name
        }
        editUndoable(undo.first, app.label, undo.second, taken = sectionId to app.id) { it.remove(sectionId, app.id) }
    }

    override fun putBack(sectionId: String, app: App) {
        if (undoable.value?.takenOut == sectionId to app.id) undoLast() else addTo(sectionId, app)
    }

    override fun moveItem(sectionId: String, key: String, by: Int) = edit { it.moveItem(sectionId, key, by) }

    override fun removeShortcut() = editUndoable(UndoKind.ShortcutRemoved, "") { it.withAllAppsShortcut(false) }

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

    override fun dissolveFolder(folderId: String) {
        val name = state.value?.layout?.sections.orEmpty()
            .flatMap { it.items }
            .filterIsInstance<HomeItem.Folder>()
            .firstOrNull { it.id == folderId }?.name.orEmpty()
        editUndoable(UndoKind.FolderDissolved, name) { it.dissolveFolder(folderId) }
    }

    override fun rename(app: App, label: String) = edit { it.rename(app.id, label) }

    override fun voiceName(app: App, name: String) = edit { it.voiceName(app.id, name) }

    override fun hide(app: App) = editUndoable(UndoKind.Hidden, app.label) { it.hide(app.id) }

    override fun unhide(app: App) = edit { it.unhide(app.id) }

    override fun showDetails(app: App): Boolean = system.showDetails(app.id.packageName)

    override fun uninstall(app: App): Boolean = system.uninstall(app.id.packageName)

    /**
     * The folder is read again every couple of seconds: a picture comes in through the phone page's
     * server in the remote's service, which tells home nothing. A listing of a few files is cheap,
     * and it is only read while the panel is open.
     */
    override fun bannerChoices(): Flow<List<WallpaperItem>> = flow {
        while (true) {
            emit(media.items().filter { it.kind == WallpaperKind.Image })
            delay(BANNER_POLL_MILLIS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun setBanner(app: App, uri: String?) {
        viewModelScope.launch { appearance.update { it.withBanner(app.id.flatten(), uri) } }
    }

    override suspend fun phonePage(): PhonePage = withContext(Dispatchers.IO) { phoneLink.current() }

    private val undoable = MutableStateFlow<Undo?>(null)

    /** The hint and the undo bar share the bottom of the screen: the bar wins. */
    private val hintShown: StateFlow<Boolean> = combine(hint, undoable) { hint, undo -> hint && undo == null }
        .stateIn(viewModelScope, WhileUiSubscribed, false)
    private var undoJob: Job? = null

    /** What was just taken off the screen and can still be put back; null once the time is up. */
    val undo: StateFlow<Undo?> = undoable.asStateFlow()

    /**
     * Puts back what [undo] names: the layout as it was before, or the row that was switched off.
     * True if it was the layout: a tile is coming back.
     */
    fun undoLast(): Boolean {
        val last = undoable.value ?: return false
        last.before?.let { before -> edit { before } }
        last.muted?.let { packageName ->
            undoable.value = null
            viewModelScope.launch { appearance.update { it.copy(mutedNotices = it.mutedNotices - packageName) } }
        }
        last.row?.let { row ->
            undoable.value = null
            viewModelScope.launch { appearance.update { if (row.key in it.hiddenRows) it.withRowToggled(row) else it } }
        }
        return last.before != null
    }

    /** Something outside the rows did not open (a notification, a search): said at the bottom of the screen. */
    internal fun say(failed: Failed) = failures.say(failed)

    /** No more notifications of [packageName] in Lumbina; [app] is its name. Offered back for a while. */
    fun muteNotices(packageName: String, app: String) {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            appearance.update { it.copy(mutedNotices = it.mutedNotices + packageName) }
            undoable.value = Undo(UndoKind.NoticesMuted, app, muted = packageName)
            delay(UNDO_MILLIS)
            undoable.value = null
        }
    }

    /** Switches [row] off from the home screen itself; [title] is its name there. Offered back for a while. */
    fun hideRow(row: HomeRow, title: String) {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            appearance.update { if (row.key in it.hiddenRows) it else it.withRowToggled(row) }
            undoable.value = Undo(UndoKind.RowHidden, title, row = row)
            delay(UNDO_MILLIS)
            undoable.value = null
        }
    }

    /** Any other change ends the offer to undo the one before: its "before" is no longer what to go back to. */
    private fun edit(transform: (HomeLayout) -> HomeLayout) {
        undoJob?.cancel()
        undoable.value = null
        viewModelScope.launch { layouts.update(transform) }
    }

    private fun editUndoable(
        kind: UndoKind,
        name: String,
        place: String = "",
        taken: Pair<String, AppId>? = null,
        transform: (HomeLayout) -> HomeLayout,
    ) {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            var before: HomeLayout? = null
            layouts.update {
                before = it
                transform(it)
            }
            undoable.value = before?.let { Undo(kind, name, place, it, takenOut = taken) }
            delay(UNDO_MILLIS)
            undoable.value = null
        }
    }

    private fun newId() = UUID.randomUUID().toString()
}
