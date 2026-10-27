package dev.lumbina.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.RecommendationsView
import dev.lumbina.tv.core.domain.home.SectionStyle
import dev.lumbina.tv.core.domain.home.TileNames
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.domain.home.isOfPrograms
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.NameDialog

/** A dialog the rows page is showing. */
private sealed interface HomePanelDialog {
    data class Rename(val category: HomeSection) : HomePanelDialog

    data object NewCategory : HomePanelDialog
}

/**
 * The rows of the home screen, in its order. OK on a row opens the row's own page: whether it is
 * shown, its heading, view, size, backing, names. Holding OK moves a row, see [MoveKeys]. While a
 * row's page is open, [onSpotlight] is told which row, so home lights it.
 */
@Composable
fun HomePanel(
    state: HomeSettingsState?,
    commands: HomeSettingsCommands,
    modifier: Modifier = Modifier,
    onSpotlight: (String?) -> Unit = {},
) {
    if (state == null) return
    var dialog by remember { mutableStateOf<HomePanelDialog?>(null) }
    var moving by remember { mutableStateOf<MovingRow?>(null) }
    // The row whose page is open, and the one left last: its row takes the focus back.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var left by remember { mutableStateOf<String?>(null) }
    val returning = remember { Returning() }
    // Kept while a row's page is open, so the list comes back where it stood.
    val list = rememberLazyListState()
    val page = open?.let { state.rowPage(it) }
    val hold = LocalParkFocus.current
    BackHandler(enabled = page != null) {
        hold()
        left = open
        open = null
    }
    if (page != null) {
        RowPage(
            page = page,
            state = state,
            commands = commands,
            onRename = { dialog = HomePanelDialog.Rename(it) },
            onDelete = {
                hold()
                open = null
                commands.removeCategory(it.id)
            },
            onSpotlight = onSpotlight,
            modifier = modifier,
        )
    } else {
        val adjust = LocalAdjust.current
        SettingsList(modifier, edges = list) {
            // The shortest way to the sizes: the slider at once, on the first row.
            item(key = "sizes") {
                SettingRow(
                    onClick = { adjust(HomeSection.FAVORITES) },
                    headlineContent = { Text(stringResource(R.string.rows_sizes)) },
                )
            }
            rowItems(
                state = state,
                moving = moving,
                onMoving = { moving = it },
                returning = returning,
                commands = commands,
                focusOn = left,
                onFocus = { left = null },
                onRow = {
                    hold()
                    open = it
                },
                onNew = { dialog = HomePanelDialog.NewCategory },
            )
        }
    }
    dialog?.let { shown -> HomePanelDialogs(shown, commands, dismiss = { dialog = null }) }
}

/** What a row's page is about: the row as home lists it, its section if it is one, and whether it is shown now. */
internal class RowPageOf(val key: String, val row: HomeRow, val section: HomeSection?, val visible: Boolean) {
    /** Rows of app tiles, as against rows of programs. */
    val ofApps: Boolean get() = !isOfPrograms(key)
}

/** The page for the row [key]; null once the row is gone (a category deleted). */
internal fun HomeSettingsState.rowPage(key: String): RowPageOf? {
    val entry = rows.firstOrNull { it.row.key == key } ?: return null
    val section = when (val row = entry.row) {
        HomeRow.Favorites -> favorites
        is HomeRow.Category -> categories.firstOrNull { it.id == row.id }
        else -> null
    }
    return RowPageOf(key, entry.row, section, entry.visible)
}

/**
 * One row's settings, in the same order for every row: whether it is shown, its heading, its view,
 * the size, the backing, the names; then what only this row has. While the page is open and the
 * row shown, home brings it up and keeps it lit, so each change shows on it.
 */
@Suppress("LongParameterList")
@Composable
private fun RowPage(
    page: RowPageOf,
    state: HomeSettingsState,
    commands: HomeSettingsCommands,
    onRename: (HomeSection) -> Unit,
    onDelete: (HomeSection) -> Unit,
    onSpotlight: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubPage(rowTitle(page))
    val rows = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { rows.requestFocus() }
    }
    val spotlight by rememberUpdatedState(onSpotlight)
    // A hidden row is not on home to bring up: home stays where it is until the row is shown.
    LaunchedEffect(page.key, page.visible) { spotlight(page.key.takeIf { page.visible }) }
    DisposableEffect(Unit) { onDispose { spotlight(null) } }
    val look = state.appearance
    Box(modifier.focusRequester(rows).focusGroup()) {
        SettingsColumn {
            SwitchRow(
                title = stringResource(R.string.home_category_show),
                hint = null,
                checked = page.visible,
                onClick = { commands.toggleRow(page.key) },
            )
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
            OwnRows(page, state, commands, onRename, onDelete)
        }
    }
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

/**
 * What only this row has: in a row of apps the tile that opens all apps and the "+", in a category
 * also its name and deleting it; in recommendations the way to the channels shown; in "continue
 * watching" its apps, each to show or take out.
 */
@Composable
private fun OwnRows(
    page: RowPageOf,
    state: HomeSettingsState,
    commands: HomeSettingsCommands,
    onRename: (HomeSection) -> Unit,
    onDelete: (HomeSection) -> Unit,
) {
    val look = state.appearance
    page.section?.let { section ->
        val shown = HomeItem.AllApps in section.items
        SwitchRow(
            title = stringResource(R.string.row_all_apps_tile),
            hint = stringResource(R.string.row_all_apps_tile_hint),
            checked = shown,
            onClick = { commands.setShortcut(section.id, !shown) },
        )
        SwitchRow(
            title = stringResource(R.string.row_plus),
            hint = stringResource(R.string.row_plus_hint),
            checked = look.showPlusIn(page.key),
            onClick = { commands.toggleRowPlus(page.key) },
        )
    }
    val category = page.section?.takeUnless { it.isFavorites }
    if (category != null) {
        SettingRow(
            onClick = { onRename(category) },
            headlineContent = { Text(stringResource(R.string.home_category_rename)) },
        )
        SettingRow(
            onClick = { onDelete(category) },
            headlineContent = { Text(stringResource(R.string.home_category_delete)) },
            supportingContent = { Text(stringResource(R.string.home_category_delete_hint)) },
        )
    }
    if (page.row == HomeRow.WatchNext) WatchNextApps(state.watchNextApps, state.watchNextMissing, commands)
    if (page.row == HomeRow.Recommendations) {
        val links = LocalSettingsLinks.current
        SettingRow(
            onClick = { links.page(SettingsPage.Channels) },
            headlineContent = { Text(stringResource(R.string.row_channels)) },
            supportingContent = { Text(stringResource(R.string.row_channels_hint)) },
        )
    }
}

/**
 * The apps of "continue watching", each to show or take out; all are shown until the user says.
 * With none, why: no provider, no access to it (granted on the channels' page), or nothing yet.
 */
@Composable
private fun WatchNextApps(apps: List<WatchNextChoice>, missing: WatchNextMissing?, commands: HomeSettingsCommands) {
    Header(stringResource(R.string.row_watch_next_apps))
    when {
        apps.isNotEmpty() -> Unit

        missing == WatchNextMissing.Provider -> Note(stringResource(R.string.channels_no_provider))

        missing == WatchNextMissing.Access -> {
            Note(stringResource(R.string.channels_access_note))
            val links = LocalSettingsLinks.current
            SettingRow(
                onClick = { links.page(SettingsPage.Channels) },
                headlineContent = { Text(stringResource(R.string.channels_access_allow)) },
            )
        }

        else -> Note(stringResource(R.string.row_watch_next_apps_none))
    }
    apps.forEach { app ->
        // Keyed: a switch that reorders the list keeps the focus on its own app.
        key(app.packageName) {
            SwitchRow(
                title = app.app,
                hint = pluralStringResource(R.plurals.channels_programs, app.programs, app.programs),
                checked = app.shown,
                onClick = { commands.toggleWatchNextApp(app.packageName) },
            )
        }
    }
}

/** The row's name as the settings and the slider head it. */
@Composable
internal fun rowTitle(page: RowPageOf): String = when {
    page.section?.isFavorites == true -> stringResource(R.string.home_row_favorites)
    page.section != null -> page.section.name
    else -> builtInTitle(page.row)
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

@Composable
private fun HomePanelDialogs(dialog: HomePanelDialog, commands: HomeSettingsCommands, dismiss: () -> Unit) {
    when (dialog) {
        is HomePanelDialog.Rename -> NameDialog(
            title = stringResource(R.string.home_category_rename),
            initial = dialog.category.name,
            saveLabel = stringResource(R.string.name_save),
            cancelLabel = stringResource(R.string.action_cancel),
            onSave = { if (it.isNotBlank()) commands.renameCategory(dialog.category.id, it.trim()) },
            onDismiss = dismiss,
            needsText = true,
        )

        HomePanelDialog.NewCategory -> NameDialog(
            title = stringResource(R.string.home_category_new),
            initial = stringResource(R.string.home_category_default_name),
            saveLabel = stringResource(R.string.name_save),
            cancelLabel = stringResource(R.string.action_cancel),
            onSave = { if (it.isNotBlank()) commands.addCategory(it.trim()) },
            onDismiss = dismiss,
            needsText = true,
        )
    }
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
