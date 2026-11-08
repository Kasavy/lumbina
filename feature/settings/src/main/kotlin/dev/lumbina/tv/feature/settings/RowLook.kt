package dev.lumbina.tv.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.RecommendationsView
import dev.lumbina.tv.core.domain.home.SectionStyle
import dev.lumbina.tv.core.domain.home.TileNames
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.Choice

/** How the row looks: its heading, its view, the size, the backing, the names. */
@Composable
internal fun LookRows(page: RowPageOf, look: HomeAppearance, commands: HomeSettingsCommands) {
    SwitchRow(
        title = stringResource(R.string.row_title),
        hint = null,
        checked = look.showTitleIn(page.key),
        onClick = { commands.toggleRowTitle(page.key) },
    )
    ViewChoice(page, look, commands)
    SizeRow(page, look.tileSizeOf(page.key))
    SwitchRow(
        title = stringResource(R.string.row_plate),
        hint = stringResource(if (page.ofApps) R.string.row_plate_hint else R.string.row_plate_hint_cards),
        checked = look.plateIn(page.key),
        onClick = { commands.toggleRowPlate(page.key) },
    )
    NamesChoices(page, look, commands)
}

/**
 * A section's layout (a row or a grid), or how recommendations come: tabs or a row per channel;
 * other rows have none.
 */
@Composable
private fun ViewChoice(page: RowPageOf, look: HomeAppearance, commands: HomeSettingsCommands) {
    val section = page.section
    val options = when {
        section != null -> SectionStyle.entries.map { style ->
            Choice(stringResource(style.choice), chosen = style == section.style) {
                commands.setStyle(section.id, style)
            }
        }

        page.row == HomeRow.Recommendations -> RecommendationsView.entries.map { view ->
            Choice(stringResource(view.choice), chosen = view == look.recommendationsView) {
                commands.setRecommendationsView(view)
            }
        }

        else -> return
    }
    ChoiceRow(title = stringResource(R.string.row_layout), options = options)
}

/** Which tiles carry names and, when some do, where: on the tiles or under them. */
@Composable
private fun NamesChoices(page: RowPageOf, look: HomeAppearance, commands: HomeSettingsCommands) {
    val names = look.namesIn(page.key)
    ChoiceRow(
        title = stringResource(R.string.row_names),
        options = TileNames.entries.map { entry ->
            Choice(stringResource(entry.choice), chosen = entry == names) { commands.setRowNames(page.key, entry) }
        },
    )
    if (names == TileNames.None) return
    val place = look.namesPlaceIn(page.key)
    ChoiceRow(
        title = stringResource(R.string.row_names_place),
        hint = stringResource(R.string.row_names_under_hint).takeIf { place == NamesPlace.Under && page.ofApps },
        options = NamesPlace.entries.map { entry ->
            Choice(stringResource(entry.choiceFor(page)), chosen = entry == place) {
                commands.setRowNamesPlace(page.key, entry)
            }
        },
    )
}

/** How big the row's tiles (or cards) are now; OK opens the slider on the home screen itself. */
@Composable
private fun SizeRow(page: RowPageOf, now: TileSize) {
    val adjust = LocalAdjust.current
    SettingRow(
        onClick = { adjust(page.key) },
        headlineContent = {
            Text(stringResource(if (page.ofApps) R.string.row_tile_size else R.string.row_card_size))
        },
        trailingContent = { Text(now.nameFor(page), style = MaterialTheme.typography.bodyMedium) },
    )
}

private val SectionStyle.choice: Int
    get() = when (this) {
        SectionStyle.Row -> R.string.row_style_row
        SectionStyle.Grid -> R.string.row_style_grid
    }

private val RecommendationsView.choice: Int
    get() = when (this) {
        RecommendationsView.Tabs -> R.string.row_view_tabs
        RecommendationsView.PerChannel -> R.string.row_view_per_channel
    }

private val TileNames.choice: Int
    get() = when (this) {
        TileNames.None -> R.string.row_names_none
        TileNames.Focused -> R.string.row_names_focused
        TileNames.All -> R.string.row_names_all
    }

/** On the tile or under it; for a row of programs, on the card or under it. */
private fun NamesPlace.choiceFor(page: RowPageOf): Int = when (this) {
    NamesPlace.OnTile -> if (page.ofApps) R.string.row_names_on_tile else R.string.row_names_on_card
    NamesPlace.Under -> if (page.ofApps) R.string.row_names_under_tile else R.string.row_names_under_card
}

/**
 * Whether a row's page shows only what keeps the row empty and the way to it, not how it would
 * look: a shown row that can't fill ("frequently used" without usage access, the channels' rows
 * without channels). A row switched off waits for nothing and keeps its look to set up beforehand.
 */
internal fun rowWaits(row: HomeRow, visible: Boolean, usageGranted: Boolean, channelsMissing: Boolean): Boolean =
    visible &&
        when (row) {
            HomeRow.Frequent -> !usageGranted
            HomeRow.WatchNext, HomeRow.Recommendations -> channelsMissing
            else -> false
        }
