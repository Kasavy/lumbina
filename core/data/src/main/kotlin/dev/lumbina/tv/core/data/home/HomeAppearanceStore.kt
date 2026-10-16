package dev.lumbina.tv.core.data.home

import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.AppsOrder
import dev.lumbina.tv.core.domain.home.ClockFormat
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.RecommendationsView
import dev.lumbina.tv.core.domain.home.RowLook
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.home.TileNames
import dev.lumbina.tv.core.domain.home.TileSize
import kotlinx.serialization.Serializable

/** On-disk form; unknown values (from a newer version) fall back to the default. */
@Serializable
internal data class StoredHomeAppearance(
    val background: String? = null,
    val tileSize: String? = null,
    /** Legacy field, kept to migrate old data: names under every tile ([names] then absent). */
    val showLabels: Boolean = false,
    val names: String? = null,
    val namesPlace: String? = null,
    val accent: String? = null,
    /** Null in data from before themes: then an accent other than the first one was a choice. */
    val accentChosen: Boolean? = null,
    val theme: String? = null,
    val textScale: String? = null,
    /** Legacy field, kept to migrate old data: false means the "recent" row is hidden. */
    val showRecentlyInstalled: Boolean = true,
    val rowOrder: List<String> = emptyList(),
    val hiddenRows: List<String> = emptyList(),
    val shownRows: List<String> = emptyList(),
    val customBanners: Map<String, String> = emptyMap(),
    val showDate: Boolean = true,
    val clockFormat: String? = null,
    val showNowPlaying: Boolean = true,
    val appsOrder: String? = null,
    val holdOkHintsLeft: Int = HomeAppearance.HOLD_OK_HINTS,
    val rowLooks: Map<String, StoredRowLook> = emptyMap(),
    val mutedNotices: List<String> = emptyList(),
    val recommendationsView: String? = null,
    val channelOrder: List<String> = emptyList(),
)

/** A row's own look; a value from a newer version reads as "as by default". */
@Serializable
internal data class StoredRowLook(
    val tileSize: String? = null,
    val names: String? = null,
    val namesPlace: String? = null,
    val showTitle: Boolean? = null,
    val showPlus: Boolean? = null,
    val plate: Boolean? = null,
)

internal fun HomeAppearance.toStored() = StoredHomeAppearance(
    background = background.name,
    tileSize = tileSize.name,
    names = names.name,
    namesPlace = namesPlace.name,
    accent = accent.name,
    accentChosen = accentChosen,
    theme = theme,
    textScale = textScale.name,
    rowOrder = rowOrder,
    hiddenRows = hiddenRows.sorted(),
    shownRows = shownRows.sorted(),
    customBanners = customBanners,
    showDate = showDate,
    clockFormat = clockFormat.name,
    showNowPlaying = showNowPlaying,
    appsOrder = appsOrder.name,
    holdOkHintsLeft = holdOkHintsLeft,
    mutedNotices = mutedNotices.sorted(),
    rowLooks = rowLooks.mapValues { (_, look) ->
        StoredRowLook(
            tileSize = look.tileSize?.name,
            names = look.names?.name,
            namesPlace = look.namesPlace?.name,
            showTitle = look.showTitle,
            showPlus = look.showPlus,
            plate = look.plate,
        )
    },
    recommendationsView = recommendationsView.name,
    channelOrder = channelOrder,
)

internal fun StoredHomeAppearance.toDomain() = HomeAppearance(
    background = HomeBackground.entries.firstOrNull { it.name == background } ?: HomeBackground.Night,
    tileSize = TileSize.entries.firstOrNull { it.name == tileSize } ?: HomeAppearance().tileSize,
    names = generalNames(),
    namesPlace = generalNamesPlace(),
    accent = Accent.entries.firstOrNull { it.name == accent } ?: Accent.Amber,
    accentChosen = accentChosen ?: (accent != null && accent != Accent.Amber.name),
    theme = theme ?: HomeAppearance.THEME_DARK,
    textScale = TextScale.entries.firstOrNull { it.name == textScale } ?: TextScale.Normal,
    rowOrder = rowOrder,
    hiddenRows = hiddenRows.toSet() + if (showRecentlyInstalled) emptySet() else setOf(HomeRow.Recent.key),
    shownRows = shownRows.toSet(),
    customBanners = customBanners,
    showDate = showDate,
    clockFormat = ClockFormat.entries.firstOrNull { it.name == clockFormat } ?: ClockFormat.System,
    showNowPlaying = showNowPlaying,
    appsOrder = AppsOrder.entries.firstOrNull { it.name == appsOrder } ?: AppsOrder.ByUse,
    mutedNotices = mutedNotices.toSet(),
    holdOkHintsLeft = holdOkHintsLeft,
    rowLooks = rowLooks.mapValues { (_, look) -> look.toDomain() }.filterValues { !it.isDefault },
    recommendationsView = RecommendationsView.entries.firstOrNull { it.name == recommendationsView }
        ?: RecommendationsView.Tabs,
    channelOrder = channelOrder,
)

/** From before there was a choice of names: every tile's under it, or none (still the default). */
private fun StoredHomeAppearance.generalNames(): TileNames =
    TileNames.entries.firstOrNull { it.name == names } ?: if (showLabels) TileNames.All else TileNames.None

private fun StoredHomeAppearance.generalNamesPlace(): NamesPlace =
    NamesPlace.entries.firstOrNull { it.name == namesPlace } ?: if (showLabels) NamesPlace.Under else NamesPlace.OnTile

private fun StoredRowLook.toDomain() = RowLook(
    tileSize = TileSize.entries.firstOrNull { it.name == tileSize },
    names = TileNames.entries.firstOrNull { it.name == names },
    namesPlace = NamesPlace.entries.firstOrNull { it.name == namesPlace },
    showTitle = showTitle,
    showPlus = showPlus,
    plate = plate,
)
