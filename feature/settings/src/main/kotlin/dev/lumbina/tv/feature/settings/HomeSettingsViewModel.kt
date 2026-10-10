package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeRowEntry
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.SectionStyle
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.domain.home.movedRow
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [rows]: every row below Favorites in the user's order, shown or not. */
data class HomeSettingsState(
    val appearance: HomeAppearance,
    val categories: List<HomeSection>,
    val hidden: List<App>,
    val rows: List<HomeRowEntry> = emptyList(),
    /** A category removed a moment ago, which can still be put back. */
    val removed: RemovedCategory? = null,
)

/**
 * A category just removed: where it stood among the sections ([at]) and among the rows listed
 * ([row]). [shown] once its offer has been on screen: the focus is given to it only the first time.
 */
data class RemovedCategory(val section: HomeSection, val at: Int, val row: Int, val shown: Boolean = false)

/** What the home settings panel can change. */
@Suppress("TooManyFunctions") // one per setting
interface HomeSettingsCommands {
    fun setBackground(background: HomeBackground)

    fun setTileSize(size: TileSize)

    fun toggleLabels()

    fun unhide(app: App)

    fun addCategory(name: String)

    fun renameCategory(id: String, name: String)

    fun toggleStyle(section: HomeSection)

    /** Moves a row (a category or a built-in one) [by] places. */
    fun moveRow(key: String, by: Int)

    /** Puts the rows back in [keys]' order, e.g. when a move is cancelled. */
    fun setRowOrder(keys: List<String>)

    fun toggleRow(key: String)

    fun removeCategory(id: String)

    /** Puts back the category removed last, while it is still offered. */
    fun undoRemove()

    /** The offer to put a category back is on screen: it stays while it has the focus, and a while after. */
    fun offerFocused(focused: Boolean)

    fun setAccent(accent: Accent)

    fun toggleTextScale()

    /** A language tag from [AppLanguage.CHOICES]; null follows the device. */
    fun setLanguage(tag: String?)
}

@Suppress("TooManyFunctions") // implements HomeSettingsCommands
@HiltViewModel
class HomeSettingsViewModel @Inject constructor(
    installedApps: InstalledApps,
    private val layouts: HomeLayoutRepository,
    private val appearance: HomeAppearanceRepository,
    private val appLanguage: AppLanguage,
) : ViewModel(),
    HomeSettingsCommands {

    private val removed = MutableStateFlow<RemovedCategory?>(null)
    private var forgetting: Job? = null

    val state: StateFlow<HomeSettingsState?> =
        combine(installedApps.observe(), layouts.layout, appearance.settings, removed) { apps, layout, look, removed ->
            HomeSettingsState(
                look,
                layout.categories,
                layout.arrange(apps, Locale.getDefault()).hidden,
                look.rows(layout.categories.map { it.id }),
                removed,
            )
        }
            // Arranging sorts every app by name: off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, WhileUiSubscribed, null)

    override fun setBackground(background: HomeBackground) = look { it.copy(background = background) }

    override fun setTileSize(size: TileSize) = look { it.copy(tileSize = size) }

    override fun toggleLabels() = look { it.copy(showLabels = !it.showLabels) }

    override fun unhide(app: App) = edit { it.unhide(app.id) }

    override fun addCategory(name: String) = edit { it.addCategory(UUID.randomUUID().toString(), name) }

    override fun renameCategory(id: String, name: String) = edit { it.renameSection(id, name) }

    override fun toggleStyle(section: HomeSection) = edit {
        it.setStyle(section.id, if (section.style == SectionStyle.Row) SectionStyle.Grid else SectionStyle.Row)
    }

    override fun moveRow(key: String, by: Int) = rows { look, rows -> look.copy(rowOrder = rows.movedRow(key, by)) }

    override fun setRowOrder(keys: List<String>) = look { it.copy(rowOrder = keys) }

    override fun toggleRow(key: String) = rows { look, rows ->
        rows.firstOrNull { it.row.key == key }?.let { look.withRowToggled(it.row) } ?: look
    }

    /** Removed at once, and offered back for a while: no question asked before, as nothing is lost yet. */
    override fun removeCategory(id: String) {
        viewModelScope.launch {
            val layout = layouts.layout.first()
            val section = layout.section(id) ?: return@launch
            val row = state.value?.rows?.indexOfFirst { it.row.key == HomeRow.Category(id).key } ?: -1
            layouts.update { it.removeCategory(id) }
            removed.value = RemovedCategory(section, layout.sections.indexOf(section), row)
            forgetLater()
        }
    }

    /** The offer goes only once the category is back: its row takes the focus the offer had. */
    override fun undoRemove() {
        val last = removed.value ?: return
        forgetting?.cancel()
        viewModelScope.launch {
            layouts.update { it.restoreCategory(last.section, last.at) }
            removed.compareAndSet(last, null)
        }
    }

    override fun offerFocused(focused: Boolean) {
        val last = removed.value ?: return
        removed.compareAndSet(last, last.copy(shown = true))
        if (focused) forgetting?.cancel() else forgetLater()
    }

    private fun forgetLater() {
        forgetting?.cancel()
        forgetting = viewModelScope.launch {
            delay(UNDO_MILLIS)
            removed.value = null
        }
    }

    override fun setAccent(accent: Accent) = look { it.copy(accent = accent) }

    /** The chosen language tag, null for the device language. */
    val language: String? get() = appLanguage.current()

    val languageSupported: Boolean get() = appLanguage.supported

    override fun setLanguage(tag: String?) {
        if (tag != appLanguage.current()) appLanguage.set(tag)
    }

    override fun toggleTextScale() = look {
        it.copy(textScale = if (it.textScale == TextScale.Large) TextScale.Normal else TextScale.Large)
    }

    private fun edit(transform: (HomeLayout) -> HomeLayout) {
        viewModelScope.launch { layouts.update(transform) }
    }

    /** Edits the look with the rows as they are now, categories read fresh from the layout. */
    private fun rows(transform: (HomeAppearance, List<HomeRowEntry>) -> HomeAppearance) {
        viewModelScope.launch {
            val categoryIds = layouts.layout.first().categories.map { it.id }
            appearance.update { transform(it, it.rows(categoryIds)) }
        }
    }

    private fun look(transform: (HomeAppearance) -> HomeAppearance) {
        viewModelScope.launch { appearance.update(transform) }
    }

    internal companion object {
        const val UNDO_MILLIS = 10_000L
    }
}
