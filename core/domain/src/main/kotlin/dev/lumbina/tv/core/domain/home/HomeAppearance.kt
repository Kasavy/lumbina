package dev.lumbina.tv.core.domain.home

import dev.lumbina.tv.core.domain.settings.SettingsRepository

/** Built-in home backgrounds. */
enum class HomeBackground { Night, Dusk, Ocean, Forest, Plum }

/**
 * How big app tiles are, as how many of them a row shows whole ([perRow]); bigger tiles mean
 * fewer. The names are those the first three were saved under.
 */
enum class TileSize(val perRow: Int) {
    Small(perRow = 6),
    Medium(perRow = 5),
    Large(perRow = 4),
    Smaller(perRow = 7),
    Smallest(perRow = 8),
}

/** Which tiles of a row carry their names: none, the focused one, all of them. */
enum class TileNames { None, Focused, All }

/**
 * Where a tile's name is: on the tile, over a shade at its bottom, taking no room; or under it,
 * in a line kept for it on every tile, so the row is taller.
 */
enum class NamesPlace { OnTile, Under }

/** How the channels' recommendations are laid out: one block with a tab per channel, or a row per channel. */
enum class RecommendationsView { Tabs, PerChannel }

/**
 * What the user set for one row where it differs from what the row has by default; null means
 * "as by default". Rows are named by their [HomeRow.key].
 */
data class RowLook(
    val tileSize: TileSize? = null,
    val names: TileNames? = null,
    val namesPlace: NamesPlace? = null,
    /** The row's heading over its tiles; shown unless switched off. */
    val showTitle: Boolean? = null,
    /** The "+" tile a row of apps ends with; there unless switched off. */
    val showPlus: Boolean? = null,
    /** The glass backing under the row's tiles. */
    val plate: Boolean? = null,
) {
    val isDefault: Boolean
        get() = tileSize == null && names == null && namesPlace == null && showTitle == null && showPlus == null &&
            plate == null
}

/**
 * The highlight color (focus, switches, progress), by its ARGB value: light enough for dark text
 * on it, bright enough to mark focus from across the room.
 */
@Suppress("MagicNumber") // the colors are the values
enum class Accent(val argb: Long) {
    Amber(0xFFFFB547),
    Sky(0xFF7CC4FF),
    Mint(0xFF6FE3B4),
    Rose(0xFFFF8FA8),
    Violet(0xFFC3A6FF),
}

/** Text size across Lumbina: Large for a far-away screen or weaker eyes. */
enum class TextScale { Normal, Large }

/** Clock format: as the device has it, or always 24 or 12 hours. */
enum class ClockFormat { System, H24, H12 }

/** How "All apps" is sorted: the most opened first (then by name), or by name only. */
enum class AppsOrder { ByUse, ByName }

data class HomeAppearance(
    val background: HomeBackground = HomeBackground.Night,
    val tileSize: TileSize = TileSize.Small,
    /** Which app tiles carry names, where a row has no choice of its own; banners usually carry the name already. */
    val names: TileNames = TileNames.None,
    /** Where app tiles carry their names, where a row has no choice of its own. */
    val namesPlace: NamesPlace = NamesPlace.OnTile,
    val accent: Accent = Accent.Amber,
    /** Whether [accent] is the user's choice; until it is, the highlight is the theme's own. */
    val accentChosen: Boolean = false,
    /** The id of a theme ([dev.lumbina.tv.core.domain.theme.Themes]), or [THEME_SYSTEM]. */
    val theme: String = THEME_DARK,
    val textScale: TextScale = TextScale.Normal,
    /** Keys of [HomeRow]s in the user's order (see [arrangeRows]); empty means the default order. */
    val rowOrder: List<String> = emptyList(),
    /** Keys of the rows the user switched off. */
    val hiddenRows: Set<String> = emptySet(),
    /** Keys of rows that start off ([HomeRow.defaultVisible]) and the user switched on. */
    val shownRows: Set<String> = emptySet(),
    /** The user's own banners: app id ([dev.lumbina.tv.core.domain.app.AppId.flatten]) to a picture URI. */
    val customBanners: Map<String, String> = emptyMap(),
    /** The date under the clock. */
    val showDate: Boolean = true,
    val clockFormat: ClockFormat = ClockFormat.System,
    /** "Now playing" with its buttons while an app plays something. */
    val showNowPlaying: Boolean = true,
    val appsOrder: AppsOrder = AppsOrder.ByUse,
    /** Home openings left that show the "hold OK" hint; 0 once the user has held OK on a tile. */
    val holdOkHintsLeft: Int = HOLD_OK_HINTS,
    /** Packages whose notifications Lumbina does not show; the device's own notifications are not touched. */
    val mutedNotices: Set<String> = emptySet(),
    /** Rows that look their own way, by row; a row not named here looks as by default. */
    val rowLooks: Map<String, RowLook> = emptyMap(),
    val recommendationsView: RecommendationsView = RecommendationsView.Tabs,
    /** Channel keys ([dev.lumbina.tv.core.domain.channels.TvChannel.key]) as the user ordered their rows. */
    val channelOrder: List<String> = emptyList(),
) {
    /** How big tiles are in the row [key]: its own size, or its default ([defaultTileSizeOf]). */
    fun tileSizeOf(key: String): TileSize = rowLooks[key]?.tileSize ?: defaultTileSizeOf(key)

    /** The row [key]'s size while it has none of its own: Favorites a size bigger, the rest the general one. */
    fun defaultTileSizeOf(key: String): TileSize = if (key == HomeSection.FAVORITES) TileSize.Medium else tileSize

    /** Which tiles of the row [key] carry names: its own choice; else all programs, and apps as in general. */
    fun namesIn(key: String): TileNames = rowLooks[key]?.names ?: if (isOfPrograms(key)) TileNames.All else names

    /** Where tiles of the row [key] carry names: its own choice; else under programs, and for apps as in general. */
    fun namesPlaceIn(key: String): NamesPlace =
        rowLooks[key]?.namesPlace ?: if (isOfPrograms(key)) NamesPlace.Under else namesPlace

    /** Whether the row [key] has its heading over it. */
    fun showTitleIn(key: String): Boolean = rowLooks[key]?.showTitle ?: true

    /** Whether the row [key] ends with the "+" tile. */
    fun showPlusIn(key: String): Boolean = rowLooks[key]?.showPlus ?: true

    /** Whether the row [key] stands on its glass backing: Favorites does unless switched off. */
    fun plateIn(key: String): Boolean = rowLooks[key]?.plate ?: (key == HomeSection.FAVORITES)

    /** Changes the row [key]'s own look; one left with no choice of its own is no longer kept. */
    fun withRowLook(key: String, change: (RowLook) -> RowLook): HomeAppearance {
        val look = change(rowLooks[key] ?: RowLook())
        return copy(rowLooks = if (look.isDefault) rowLooks - key else rowLooks + (key to look))
    }

    /** The rows as [arrangeRows] sees them, for these settings. */
    fun rows(categoryIds: List<String>): List<HomeRowEntry> = arrangeRows(rowOrder, hiddenRows, categoryIds, shownRows)

    /** Shows [row] if it is hidden, hides it otherwise. */
    fun withRowToggled(row: HomeRow): HomeAppearance = if (row.defaultVisible) {
        copy(hiddenRows = if (row.key in hiddenRows) hiddenRows - row.key else hiddenRows + row.key)
    } else {
        copy(shownRows = if (row.key in shownRows) shownRows - row.key else shownRows + row.key)
    }

    companion object {
        const val HOLD_OK_HINTS = 3

        /** The look Lumbina has always had. */
        const val THEME_DARK = "dark"

        /** Dark or light, as the device's own settings have it. */
        const val THEME_SYSTEM = "system"
    }
}

typealias HomeAppearanceRepository = SettingsRepository<HomeAppearance>

/** Whether the row [key] holds programs (continue watching, the channels) rather than app tiles. */
fun isOfPrograms(key: String): Boolean =
    key == HomeRow.WatchNext.key || key == HomeRow.Recommendations.key || key.startsWith(CHANNEL_ROW_PREFIX)

/** The row key of the channel [channelKey] when recommendations come a row per channel. */
fun channelRowKey(channelKey: String): String = CHANNEL_ROW_PREFIX + channelKey

private const val CHANNEL_ROW_PREFIX = "channel:"

/** Shows the picture at [uri] as the banner of the app with [key]; null brings back the app's own. */
fun HomeAppearance.withBanner(key: String, uri: String?): HomeAppearance =
    copy(customBanners = if (uri == null) customBanners - key else customBanners + (key to uri))
