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
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.isOfPrograms
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
 * row shown, home brings it up and keeps it lit, so each change shows on it. A shown row that
 * can't fill yet ("frequently used" without usage access, channels' rows without channels) has
 * only what keeps it empty and the way to it: how it would look changes nothing on home.
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
            // Only for a row that is shown: one switched off waits for nothing. Read as the page is
            // first drawn, then as GrantNeeded reads it again (back from the system's screen).
            var usageGranted by remember { mutableStateOf(commands.usageGrant.granted()) }
            if (page.row == HomeRow.Frequent && page.visible) {
                GrantNeeded(
                    commands.usageGrant,
                    note = stringResource(R.string.grant_usage_note),
                    allow = stringResource(R.string.grant_usage_allow),
                    onRead = { usageGranted = it },
                )
            }
            if (!rowWaits(page.row, page.visible, usageGranted, state.channelsMissing != null)) {
                LookRows(page, look, commands)
            }
            OwnRows(page, state, commands, onRename, onDelete)
        }
    }
}

/**
 * What only this row has: in a row of apps the tile that opens all apps and the "+", in a category
 * also its name and deleting it; in recommendations the way to the channels shown, or what keeps
 * channels away; in "continue watching" its apps, each to show or take out, or what keeps them
 * away. ("Frequently used"'s usage access comes right under "show", in [RowPage].)
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
    if (page.row == HomeRow.WatchNext) WatchNextApps(state.watchNextApps, state.channelsMissing, commands)
    // The channels' page may have given the access since: worked out again as the page opens.
    if (page.row == HomeRow.WatchNext || page.row == HomeRow.Recommendations) {
        LaunchedEffect(Unit) { commands.recheckChannels() }
    }
    val missing = state.channelsMissing
    if (page.row == HomeRow.Recommendations && missing != null) ChannelsMissing(missing)
    // Which channels to show: once there are any to choose from.
    if (page.row == HomeRow.Recommendations && missing == null) {
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
 * Over them what keeps channels away, if anything (no provider, no access to it, granted on the
 * channels' page); with no app and nothing in the way, that there is nothing yet.
 */
@Composable
private fun WatchNextApps(apps: List<WatchNextChoice>, missing: WatchNextMissing?, commands: HomeSettingsCommands) {
    Header(stringResource(R.string.row_watch_next_apps))
    // What keeps channels away is said even over apps kept from before: the row's look waits for it.
    when {
        missing != null -> ChannelsMissing(missing)
        apps.isEmpty() -> Note(stringResource(R.string.row_watch_next_apps_none))
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
