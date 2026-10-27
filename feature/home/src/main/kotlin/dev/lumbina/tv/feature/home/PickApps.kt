package dev.lumbina.tv.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.BANNER_ASPECT
import dev.lumbina.tv.core.ui.CenteredPanel
import dev.lumbina.tv.core.ui.PanelGroup
import dev.lumbina.tv.core.ui.TICK
import dev.lumbina.tv.core.ui.TileCard
import dev.lumbina.tv.core.ui.TileName
import dev.lumbina.tv.core.ui.onTile
import dev.lumbina.tv.core.ui.requestFocusSoon
import java.util.Locale

/**
 * The last tile of a row of apps: "+" in a dashed outline. OK opens the apps to tick for this row,
 * so several go in at once and without the settings. It is not moved and has no menu.
 */
@Composable
internal fun PlusTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = OUTLINE_ALPHA)
    val label = stringResource(R.string.pick_apps_add)
    TileCard(
        onClick = onClick,
        containerColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = FILL_ALPHA),
        modifier = modifier.aspectRatio(BANNER_ASPECT).semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // The dashes are made once per size, not on every frame of a scrolling home.
            modifier = Modifier.fillMaxSize().drawWithCache {
                val dash = DASH.toPx()
                val stroke = Stroke(STROKE.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
                val corner = CornerRadius(CORNER.toPx())
                onDrawBehind { drawRoundRect(color = outline, cornerRadius = corner, style = stroke) }
            },
        ) {
            Text(
                "+",
                style = MaterialTheme.typography.displaySmall.onTile(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Every app as a tile, ticked if the section shows it (in the row or in a folder of it): first
 * those in the row, in its order, then the rest; the tile that opens all apps leads the group it
 * was in. OK puts a tile in or takes it out at once. The groups stay as they were when the window
 * opened, so a tile never runs away from the focus.
 */
@Composable
internal fun PickApps(dialog: HomeDialog.PickApps, state: HomeUiState, commands: HomeCommands, dismiss: () -> Unit) {
    val section = state.layout.section(dialog.sectionId)
    if (section == null) {
        LaunchedEffect(dialog, dismiss) { dismiss() }
        return
    }
    val opened = remember(dialog) { section }
    val apps = state.content.apps
    val groups = remember(opened, apps) { pickGroups(opened, apps, Locale.getDefault()) }
    val shown = remember(section) { section.apps.toSet() }
    val count = remember(section, apps) { chosenCount(section, apps) }
    val first = remember { FocusRequester() }
    val shortcutInRow = remember(dialog) { HomeItem.AllApps in opened.items }
    val start = pickStart(shortcutInRow, groups.inRow.isEmpty(), state.inputs.isNotEmpty())
    val focusIf = { at: PickStart -> if (start == at) Modifier.focusRequester(first) else Modifier }
    val title = if (section.isFavorites) {
        stringResource(R.string.pick_apps_title_favorites)
    } else {
        stringResource(R.string.pick_apps_title, section.name)
    }
    CenteredPanel(
        title = title,
        onDismiss = dismiss,
        titleEnd = stringResource(R.string.pick_apps_count, count),
        subtitle = stringResource(
            if (state.inputs.isEmpty()) R.string.pick_apps_hint else R.string.pick_apps_hint_inputs,
        ),
    ) {
        val tiles: LazyGridScope.(List<App>) -> Unit = { apps ->
            items(apps, key = { app -> app.id.flatten() }) { app ->
                val inIt = app.id in shown
                val folder = folderOf(section, app.id)
                PickTile(
                    app = app,
                    checked = inIt,
                    onClick = { commands.toggle(section.id, app, inIt, inFolder = folder != null) },
                    banner = state.appearance.customBanners[app.id.flatten()],
                    folder = folder,
                    modifier = if (app == groups.first) focusIf(PickStart.Apps) else Modifier,
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(PICK_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // Room for the focused tile to grow and for the badges over the tiles' corners.
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            pickGrid(
                groups,
                shortcutInRow,
                shortcut = { shortcutItem(section, commands, focusIf(PickStart.Shortcut)) },
                inputs = { inputItems(state, section, commands, focusIf(PickStart.Inputs)) },
                tiles = tiles,
            )
        }
    }
    LaunchedEffect(Unit) { first.requestFocusSoon() }
}

/**
 * The two groups, the tile that opens all apps leading the one it was in ([shortcutInRow]), and
 * the device's [inputs] between them.
 */
private fun LazyGridScope.pickGrid(
    groups: PickGroups,
    shortcutInRow: Boolean,
    shortcut: LazyGridScope.() -> Unit,
    inputs: LazyGridScope.() -> Unit,
    tiles: LazyGridScope.(List<App>) -> Unit,
) {
    if (shortcutInRow || groups.inRow.isNotEmpty()) {
        group(R.string.pick_apps_in_row)
        if (shortcutInRow) shortcut()
        tiles(groups.inRow)
    }
    inputs()
    if (!shortcutInRow || groups.rest.isNotEmpty()) {
        group(R.string.pick_apps_rest)
        if (!shortcutInRow) shortcut()
        tiles(groups.rest)
    }
}

/**
 * The tile that opens all apps, with its badge as [PickTile] has: ticked if [section] has it; OK
 * puts it in or takes it out.
 */
private fun LazyGridScope.shortcutItem(section: HomeSection, commands: HomeCommands, modifier: Modifier) {
    item(key = HomeItem.AllApps.key) {
        val inIt = HomeItem.AllApps in section.items
        Box(modifier) {
            AllAppsTile(
                onClick = { commands.setAllApps(section.id, !inIt) },
                label = stringResource(R.string.home_all_apps),
                name = TileName.None,
            )
            TickBadge(inIt, Modifier.align(Alignment.TopEnd).offset(x = BADGE_OUT, y = -BADGE_OUT))
        }
    }
}

/**
 * Ticks [app] in the section or unticks it. The last app out of a folder takes the folder with it:
 * one in a folder is taken out as the menu does, offered back, and ticking it again while that is
 * offered brings both back where they were.
 */
private fun HomeCommands.toggle(sectionId: String, app: App, ticked: Boolean, inFolder: Boolean) = when {
    !ticked -> putBack(sectionId, app)
    inFolder -> takeOut(sectionId, app)
    else -> removeFrom(sectionId, app)
}

/** A caption across the whole width of the grid. */
internal fun LazyGridScope.group(@StringRes title: Int) {
    item(key = "group:$title", span = { GridItemSpan(maxLineSpan) }) { PanelGroup(stringResource(title)) }
}

/** An app with its badge on the top right corner: a tick when [checked], an empty ring when not. */
@Composable
private fun PickTile(
    app: App,
    checked: Boolean,
    onClick: () -> Unit,
    banner: String?,
    folder: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        AppCard(
            app = app,
            onClick = onClick,
            banner = banner,
            caption = folder?.let { stringResource(R.string.pick_apps_in_folder, it) },
        )
        TickBadge(checked, Modifier.align(Alignment.TopEnd).offset(x = BADGE_OUT, y = -BADGE_OUT))
    }
}

/** A tick in a filled circle when [checked], an empty ring when not; it sits on a tile's top right corner. */
@Composable
internal fun TickBadge(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(BADGE)
            .background(if (checked) colors.primary else colors.surface, CircleShape)
            .border(2.dp, if (checked) colors.surface else colors.onSurfaceVariant, CircleShape),
    ) {
        if (checked) Text(TICK, style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
    }
}

internal const val PLUS_KEY = "plus"
private const val PICK_COLUMNS = 5
private const val OUTLINE_ALPHA = 0.7f
private const val FILL_ALPHA = 0.5f
private val DASH = 8.dp
private val STROKE = 2.dp
private val CORNER = 8.dp
private val BADGE = 24.dp

/** How far the badge stands out of the tile's corner: a quarter of it, so it reads as sitting on the corner. */
internal val BADGE_OUT = 6.dp
