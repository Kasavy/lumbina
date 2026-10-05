package dev.lumbina.tv.core.data.home

import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.AppsOrder
import dev.lumbina.tv.core.domain.home.ClockFormat
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.home.TileSize
import kotlinx.serialization.Serializable

/** On-disk form; unknown backgrounds (from a newer version) fall back to the default. */
@Serializable
internal data class StoredHomeAppearance(
    val background: String? = null,
    val tileSize: String? = null,
    val showLabels: Boolean = false,
    val accent: String? = null,
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
)

internal fun HomeAppearance.toStored() = StoredHomeAppearance(
    background = background.name,
    tileSize = tileSize.name,
    showLabels = showLabels,
    accent = accent.name,
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
)

internal fun StoredHomeAppearance.toDomain() = HomeAppearance(
    background = HomeBackground.entries.firstOrNull { it.name == background } ?: HomeBackground.Night,
    tileSize = TileSize.entries.firstOrNull { it.name == tileSize } ?: TileSize.Medium,
    showLabels = showLabels,
    accent = Accent.entries.firstOrNull { it.name == accent } ?: Accent.Amber,
    textScale = TextScale.entries.firstOrNull { it.name == textScale } ?: TextScale.Normal,
    rowOrder = rowOrder,
    hiddenRows = hiddenRows.toSet() + if (showRecentlyInstalled) emptySet() else setOf(HomeRow.Recent.key),
    shownRows = shownRows.toSet(),
    customBanners = customBanners,
    showDate = showDate,
    clockFormat = ClockFormat.entries.firstOrNull { it.name == clockFormat } ?: ClockFormat.System,
    showNowPlaying = showNowPlaying,
    appsOrder = AppsOrder.entries.firstOrNull { it.name == appsOrder } ?: AppsOrder.ByUse,
    holdOkHintsLeft = holdOkHintsLeft,
)
