package dev.lumbina.tv.core.domain.home

import dev.lumbina.tv.core.domain.settings.SettingsRepository

/** Built-in home backgrounds. */
enum class HomeBackground { Night, Dusk, Ocean, Forest, Plum }

/** How big app tiles are; bigger tiles mean fewer per row. */
enum class TileSize { Small, Medium, Large }

/** The highlight color: focus, switches, progress. */
enum class Accent { Amber, Sky, Mint, Rose, Violet }

/** Text size across Lumbina: Large for a far-away screen or weaker eyes. */
enum class TextScale { Normal, Large }

/** Clock format: as the device has it, or always 24 or 12 hours. */
enum class ClockFormat { System, H24, H12 }

/** How "All apps" is sorted: the most opened first (then by name), or by name only. */
enum class AppsOrder { ByUse, ByName }

data class HomeAppearance(
    val background: HomeBackground = HomeBackground.Night,
    val tileSize: TileSize = TileSize.Medium,
    /** Names under the tiles; banners usually carry the app's name already. */
    val showLabels: Boolean = false,
    val accent: Accent = Accent.Amber,
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
) {
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
    }
}

typealias HomeAppearanceRepository = SettingsRepository<HomeAppearance>

/** Shows the picture at [uri] as the banner of the app with [key]; null brings back the app's own. */
fun HomeAppearance.withBanner(key: String, uri: String?): HomeAppearance =
    copy(customBanners = if (uri == null) customBanners - key else customBanners + (key to uri))
