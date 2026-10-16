package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppUsage
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.labelsByPackage
import dev.lumbina.tv.core.domain.channels.ChannelSettingsRepository
import dev.lumbina.tv.core.domain.channels.TvChannels
import dev.lumbina.tv.core.domain.channels.byAppUse
import dev.lumbina.tv.core.domain.channels.inChannelOrder
import dev.lumbina.tv.core.domain.channels.movedChannel
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeRowEntry
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.RecommendationsView
import dev.lumbina.tv.core.domain.home.RowSpotlight
import dev.lumbina.tv.core.domain.home.SectionStyle
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.home.TileNames
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.domain.home.movedRow
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.ui.UNDO_MILLIS
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

/**
 * [rows]: every row of home in the user's order, shown or not. [channels]: every channel apps
 * published, in the order of their rows when recommendations come a row per channel.
 */
data class HomeSettingsState(
    val appearance: HomeAppearance,
    val categories: List<HomeSection>,
    val hidden: List<App>,
    val rows: List<HomeRowEntry> = emptyList(),
    /** A category removed a moment ago, which can still be put back. */
    val removed: RemovedCategory? = null,
    val favorites: HomeSection = HomeSection.emptyFavorites(),
    val channels: List<ChannelChoice> = emptyList(),
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

    /** The row [key]'s own tile size; null puts it back to the general one. */
    fun setRowTileSize(key: String, size: TileSize?)

    /**
     * Moves the row [key]'s tile size [by] steps along [sizes], from the size stored now, not as
     * last shown: quick presses each count.
     */
    fun stepRowTileSize(key: String, by: Int, sizes: List<TileSize>)

    /** Every row's own tile size at once, as [sizes] has them; a row not named goes back to the general one. */
    fun setRowTileSizes(sizes: Map<String, TileSize?>)

    /** Which tiles of the row [key] carry names. */
    fun setRowNames(key: String, names: TileNames)

    /** Where tiles of the row [key] carry names: on them, or under them. */
    fun setRowNamesPlace(key: String, place: NamesPlace)

    /** Puts the row [key] on its glass backing, or takes it off. */
    fun toggleRowPlate(key: String)

    /** Recommendations as one block with tabs, or a row per channel. */
    fun setRecommendationsView(view: RecommendationsView)

    /** Moves a channel's row [by] places among the channels. */
    fun moveChannel(key: String, by: Int)

    /** Puts the channels back in [keys]' order, e.g. when a move is cancelled. */
    fun setChannelOrder(keys: List<String>)

    /** Shows the channel if it is hidden, hides it otherwise: the same switch as on the "Channels" page. */
    fun toggleChannel(key: String)

    /** Shows the row [key]'s heading, or hides it. */
    fun toggleRowTitle(key: String)

    /** Ends the row [key] with the "+" tile, or not. */
    fun toggleRowPlus(key: String)

    fun addCategory(name: String)

    fun renameCategory(id: String, name: String)

    fun setStyle(sectionId: String, style: SectionStyle)

    /** Whether Favorites has the tile that opens all apps. */
    fun setShortcut(shown: Boolean)

    /** Moves a row [by] places. */
    fun moveRow(key: String, by: Int)

    /** Puts the rows back in [keys]' order, e.g. when a move is cancelled. */
    fun setRowOrder(keys: List<String>)

    fun toggleRow(key: String)

    fun removeCategory(id: String)

    /** Puts back the category removed last, while it is still offered. */
    fun undoRemove()

    /** The offer to put a category back is on screen: it stays while it has the focus, and a while after. */
    fun offerFocused(focused: Boolean)

    /** The highlight color; null follows the theme. */
    fun setAccent(accent: Accent?)

    /** A theme's id, or [HomeAppearance.THEME_SYSTEM]. */
    fun setTheme(theme: String)

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
    private val spotlight: RowSpotlight,
    channels: TvChannels,
    private val channelSettings: ChannelSettingsRepository,
    usage: AppUsage,
) : ViewModel(),
    HomeSettingsCommands {

    private val removed = MutableStateFlow<RemovedCategory?>(null)
    private var forgetting: Job? = null

    /** Every channel, shown or not, in the order home has them by default: see ChannelsViewModel. */
    private val channelChoices = combine(
        channels.channels(),
        channels.watchNext(),
        channelSettings.settings,
        installedApps.observe(),
        usage.recent(),
    ) { feeds, watchNext, choice, apps, used ->
        val labels = labelsByPackage(apps)
        val watching = watchNext.filter { it.packageName in labels }.map { it.packageName }.toSet()
        feeds.byAppUse(used, watching).mapNotNull { feed ->
            val app = labels[feed.channel.packageName] ?: return@mapNotNull null
            val key = feed.channel.key
            ChannelChoice(key, feed.channel.name, app, feed.programs.size, key !in choice.hiddenChannels)
        }
    }

    val state: StateFlow<HomeSettingsState?> =
        combine(installedApps.observe(), layouts.layout, appearance.settings, removed) { apps, layout, look, removed ->
            HomeSettingsState(
                look,
                layout.categories,
                layout.arrange(apps, Locale.getDefault()).hidden,
                look.rows(layout.categories.map { it.id }),
                removed,
                layout.favorites,
            )
        }
            .combine(channelChoices) { state, all ->
                state.copy(channels = all.inChannelOrder(state.appearance.channelOrder) { it.key })
            }
            // Arranging sorts every app by name: off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, WhileUiSubscribed, null)

    override fun setBackground(background: HomeBackground) = look { it.copy(background = background) }

    override fun setRowTileSize(key: String, size: TileSize?) =
        look { it.withRowLook(key) { row -> row.copy(tileSize = size) } }

    override fun stepRowTileSize(key: String, by: Int, sizes: List<TileSize>) = look { look ->
        val next = sizes.getOrNull(sizes.nearest(look.tileSizeOf(key)) + by)
        if (next == null) look else look.withRowLook(key) { row -> row.copy(tileSize = next) }
    }

    override fun setRowTileSizes(sizes: Map<String, TileSize?>) = look { look ->
        (look.rowLooks.keys + sizes.keys).fold(look) { next, key ->
            next.withRowLook(key) { row -> row.copy(tileSize = sizes[key]) }
        }
    }

    /** Tells the home screen which row is being set, so it brings the row into view and lights it; null: none. */
    fun spotlight(key: String?) = spotlight.show(key)

    /** The row lit now, to put back when something lights another for a while (the size slider). */
    val spotlit: String? get() = spotlight.key.value

    override fun setRowNames(key: String, names: TileNames) =
        look { it.withRowLook(key) { row -> row.copy(names = names) } }

    override fun setRowNamesPlace(key: String, place: NamesPlace) =
        look { it.withRowLook(key) { row -> row.copy(namesPlace = place) } }

    // Each toggle reads the value stored now: a quick second OK turns it back.
    override fun toggleRowPlate(key: String) =
        look { it.withRowLook(key) { row -> row.copy(plate = !it.plateIn(key)) } }

    override fun setRecommendationsView(view: RecommendationsView) = look { it.copy(recommendationsView = view) }

    override fun moveChannel(key: String, by: Int) {
        viewModelScope.launch {
            // The channels as listed on the page; asked anew only when the page has none yet.
            val all = (state.value?.channels ?: channelChoices.first()).map { it.key }
            // From the order as stored now, not as last shown: quick presses each move one place.
            appearance.update {
                val order = all.inChannelOrder(it.channelOrder) { channel -> channel }
                it.copy(channelOrder = order.movedChannel(key, by))
            }
        }
    }

    override fun setChannelOrder(keys: List<String>) = look { it.copy(channelOrder = keys) }

    override fun toggleChannel(key: String) {
        viewModelScope.launch {
            channelSettings.update {
                val hidden = if (key in it.hiddenChannels) it.hiddenChannels - key else it.hiddenChannels + key
                it.copy(hiddenChannels = hidden)
            }
        }
    }

    // "Shown" is how a row starts: only "off" is kept.
    override fun toggleRowTitle(key: String) =
        look { it.withRowLook(key) { row -> row.copy(showTitle = if (it.showTitleIn(key)) false else null) } }

    override fun toggleRowPlus(key: String) =
        look { it.withRowLook(key) { row -> row.copy(showPlus = if (it.showPlusIn(key)) false else null) } }

    override fun addCategory(name: String) = edit { it.addCategory(UUID.randomUUID().toString(), name) }

    override fun renameCategory(id: String, name: String) = edit { it.renameSection(id, name) }

    override fun setStyle(sectionId: String, style: SectionStyle) = edit { it.setStyle(sectionId, style) }

    override fun setShortcut(shown: Boolean) = edit { it.withAllAppsShortcut(shown) }

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

    override fun setAccent(accent: Accent?) =
        look { it.copy(accent = accent ?: it.accent, accentChosen = accent != null) }

    override fun setTheme(theme: String) = look { it.copy(theme = theme) }

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
}
