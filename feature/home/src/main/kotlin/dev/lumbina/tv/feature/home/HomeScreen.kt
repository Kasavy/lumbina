package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.SectionContent
import dev.lumbina.tv.core.domain.home.SectionStyle
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.FolderCard
import dev.lumbina.tv.core.ui.PivotScrolling
import dev.lumbina.tv.core.ui.TileAnchor
import dev.lumbina.tv.core.ui.anchorWhenFocused

// The dialogs only see the HomeCommands interface, not the view model's state.
@Suppress("ViewModelForwarding")
@Composable
fun HomeRoute(
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier,
    refocus: Int = 0,
    visible: Boolean = true,
    over: ScreenOnTop? = null,
    header: @Composable () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    channelsViewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels by channelsViewModel.state.collectAsStateWithLifecycle()
    val hint by viewModel.holdOkHint.collectAsStateWithLifecycle()
    LaunchedEffect(refocus, visible) { if (visible) viewModel.onHomeShown() }
    val menu = remember { MenuState() }
    var moving by remember { mutableStateOf<Moving?>(null) }
    val (current, shownChannels) = heldWhileHidden(state to channels, visible)
    val latest by rememberUpdatedState(current)
    val openAllApps by rememberUpdatedState(onOpenAllApps)
    // One instance, so tiles taking it can skip recomposing when home does.
    val actions = remember(viewModel, channelsViewModel) {
        HomeActions(
            open = viewModel::open,
            openFolder = { menu.dialog = HomeDialog.OpenFolder(it.id) },
            appMenu = { app, place ->
                viewModel.onMenuOpened()
                val section = (place as? Place.InSection)?.sectionId.orEmpty()
                menu.open(HomeDialog.AppActions(app, place), Moving(section, HomeItem.AppItem(app.id).key))
            },
            folderMenu = { sectionId, folder ->
                viewModel.onMenuOpened()
                menu.open(HomeDialog.FolderActions(sectionId, folder), Moving(sectionId, folder.key))
            },
            move = { by -> moving?.let { viewModel.moveItem(it.sectionId, it.key, by) } },
            stopMoving = { moving = null },
            cancelMoving = {
                moving?.let(viewModel::putBack)
                moving = null
            },
            openProgram = channelsViewModel::open,
            banner = { app -> latest?.appearance?.customBanners?.get(app.id.flatten()) },
            openAllApps = { openAllApps() },
            anchor = menu.anchor,
        )
    }
    Box(modifier.hiddenFromFocus(!visible, over)) {
        HomeScreen(
            state = current,
            channels = shownChannels,
            moving = moving,
            menuTile = menu.tile.takeIf { menu.dialog != null },
            actions = actions,
            refocus = refocus,
            visible = visible,
            header = header,
        )
        HoldOkHint(visible = hint, modifier = Modifier.align(Alignment.BottomCenter))
    }
    val shown = menu.dialog
    if (current != null && shown != null) {
        HomeDialogs(
            dialog = shown,
            state = current,
            commands = viewModel,
            show = { menu.dialog = it },
            startMoving = { moving = it.copy(from = current.indexOf(it)) },
            anchor = menu.at,
        )
    }
}

/** Where [tile] is in its section now; -1 if it isn't there. */
internal fun HomeUiState.indexOf(tile: Moving): Int =
    content.sections.firstOrNull { it.id == tile.sectionId }?.tiles?.indexOfFirst { it.key == tile.key } ?: -1

/** A tile being moved with ◀ ▶ (or whose menu is open): its section, item key and where it started. */
data class Moving(val sectionId: String, val key: String, val from: Int = -1) {
    fun isTile(sectionId: String, key: String): Boolean = this.sectionId == sectionId && this.key == key
}

/** What the home screen can ask for; grouped to keep signatures short. */
class HomeActions(
    val open: (App) -> Unit,
    val openFolder: (TileContent.FolderTile) -> Unit,
    val appMenu: (App, Place) -> Unit,
    val folderMenu: (String, TileContent.FolderTile) -> Unit,
    val move: (Int) -> Unit,
    /** OK: the tile stays where it is now. */
    val stopMoving: () -> Unit,
    /** Back: the tile goes back where it was. */
    val cancelMoving: () -> Unit,
    val openProgram: (ProgramCard) -> Unit,
    /** The user's own banner for an app, if any. */
    val banner: (App) -> String?,
    val openAllApps: () -> Unit,
    /** Tiles report here while focused, so a menu can open under the tile. */
    val anchor: TileAnchor,
)

/**
 * Favorites (ending with the way to all apps), then the rows in the user's order: continue
 * watching, recently installed, categories (rows or grids) and the channels' recommendations.
 * Holding OK on a tile opens its menu.
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState?,
    channels: ChannelsUi,
    moving: Moving?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    menuTile: Moving? = null,
    refocus: Int = 0,
    visible: Boolean = true,
    header: @Composable () -> Unit = {},
) {
    val last = rememberSaveable { mutableStateOf<FocusKey?>(null) }
    val focus = remember { HomeFocus(last) }
    val grid = rememberLazyGridState()
    RestoreFocus(focus, state, channels, grid, refocus, visible)
    CompositionLocalProvider(LocalHomeFocus provides focus) {
        HomeGrid(state, channels, moving, menuTile, actions, grid, header, modifier.then(focus.area))
    }
}

@Composable
@Suppress("LongParameterList")
private fun HomeGrid(
    state: HomeUiState?,
    channels: ChannelsUi,
    moving: Moving?,
    menuTile: Moving?,
    actions: HomeActions,
    grid: LazyGridState,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = state?.appearance?.tileSize ?: TileSize.Medium
    val watchNextTitle = stringResource(R.string.home_watch_next)
    val favoritesTitle = stringResource(R.string.home_favorites)
    val frequentTitle = stringResource(R.string.home_frequent)
    val openedTitle = stringResource(R.string.home_opened)
    val recentTitle = stringResource(R.string.home_recently_installed)
    PivotScrolling {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Fixed(size.columns),
            contentPadding = GRID_PADDING,
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
            modifier = modifier.fillMaxSize(),
        ) {
            fullWidth("header") { header() }
            if (state == null) {
                homeLoading(favoritesTitle, size)
                return@LazyVerticalGrid
            }
            val showLabels = state.appearance.showLabels
            state.content.sections.firstOrNull()?.let { favorites ->
                section(
                    favorites,
                    size,
                    showLabels,
                    Marks(moving, menuTile),
                    actions,
                    allApps = state.content.apps.size,
                )
            }
            state.rows.forEach { row ->
                when (row) {
                    HomeRow.WatchNext -> if (channels.loaded) {
                        programRow(HomeRow.WatchNext.key, watchNextTitle, channels.watchNext, size, actions)
                    } else {
                        programRowLoading(HomeRow.WatchNext.key, watchNextTitle, size)
                    }

                    HomeRow.Opened -> appRow(HomeRow.Opened.key, openedTitle, state.opened, size, showLabels, actions)

                    HomeRow.Frequent ->
                        appRow(HomeRow.Frequent.key, frequentTitle, state.frequent, size, showLabels, actions)

                    HomeRow.Recent -> appRow(HomeRow.Recent.key, recentTitle, state.recent, size, showLabels, actions)

                    HomeRow.Recommendations -> if (channels.rows.isNotEmpty()) {
                        fullWidth("recommendations") { Recommendations(channels.rows, size, actions) }
                    }

                    is HomeRow.Category -> state.content.sections.firstOrNull { it.id == row.id }?.let {
                        section(it, size, showLabels, Marks(moving, menuTile), actions)
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.section(
    section: SectionContent,
    size: TileSize,
    showLabels: Boolean,
    marks: Marks,
    actions: HomeActions,
    allApps: Int? = null,
) {
    fullWidth("title:${section.id}") {
        val title = if (section.isFavorites) stringResource(R.string.home_favorites) else section.name
        val moved = marks.moving?.takeIf { it.sectionId == section.id }
            ?.let { m -> section.tiles.firstOrNull { it.key == m.key } }
        if (moved == null) SectionTitle(title) else MovingTitle(title, moved.name())
    }
    if (section.tiles.isEmpty()) {
        fullWidth("empty:${section.id}") {
            val hint = if (section.isFavorites) R.string.home_favorites_empty else R.string.home_category_empty
            Hint(stringResource(hint))
        }
        if (allApps == null) return
    }
    val movingHere = marks.moving?.sectionId == section.id
    val trailing: (@Composable (Modifier) -> Unit)? = allApps?.let { count ->
        { modifier ->
            AllAppsTile(
                count,
                actions.openAllApps,
                modifier.dimmedIf(movingHere).homeFocus(LocalHomeFocus.current, FocusKey(section.id, ALL_APPS_KEY)),
            )
        }
    }
    when (section.style) {
        SectionStyle.Grid -> {
            items(section.tiles, key = { "${section.id}:${it.key}" }, contentType = { TILE }) { tile ->
                val focus = Modifier.homeFocus(LocalHomeFocus.current, FocusKey(section.id, tile.key))
                SectionTile(section.id, tile, showLabels, marks, actions, focus)
            }
            if (trailing != null) item(key = ALL_APPS_KEY, contentType = ALL_APPS_KEY) { trailing(Modifier) }
        }

        SectionStyle.Row -> fullWidth("row:${section.id}") {
            SectionRow(section, size.rowTileWidth, marks.moving, trailing = trailing) { tile, modifier ->
                SectionTile(
                    section.id,
                    tile,
                    showLabels,
                    marks,
                    actions,
                    modifier.homeFocus(LocalHomeFocus.current, FocusKey(section.id, tile.key)),
                )
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: SectionContent,
    tileWidth: Dp,
    moving: Moving?,
    modifier: Modifier = Modifier,
    trailing: (@Composable (Modifier) -> Unit)? = null,
    tile: @Composable (TileContent, Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val movingIndex = section.tiles.indexOfFirst { moving?.isTile(section.id, it.key) == true }
    LaunchedEffect(movingIndex) { if (movingIndex >= 0) listState.animateScrollToItem(movingIndex) }
    HomeRow(modifier, listState) {
        items(section.tiles, key = { it.key }) { tile(it, Modifier.width(tileWidth)) }
        if (trailing != null) item(key = ALL_APPS_KEY) { trailing(Modifier.width(tileWidth)) }
    }
}

/**
 * One tile of a section. While it is being moved it is lifted with ◀ ▶ at its sides and its
 * neighbours dimmed: ◀ ▶ shift it, OK puts it down there, Back puts it back where it was; other
 * keys are swallowed so focus stays on it.
 */
@Composable
private fun SectionTile(
    sectionId: String,
    tile: TileContent,
    showLabels: Boolean,
    marks: Marks,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val isMoving = marks.moving?.isTile(sectionId, tile.key) == true
    val marked = isMoving || marks.menuTile?.isTile(sectionId, tile.key) == true
    val dimmed = marks.moving?.sectionId == sectionId && !isMoving
    Box(
        modifier
            .dimmedIf(dimmed)
            .anchorWhenFocused(actions.anchor)
            .onPreviewKeyEvent { event -> isMoving && moveKey(event.key, event.type, actions) },
    ) {
        val lifted = if (isMoving) Modifier.lifted() else Modifier
        when (tile) {
            is TileContent.AppTile -> AppCard(
                app = tile.app,
                banner = actions.banner(tile.app),
                onClick = { if (isMoving) actions.stopMoving() else actions.open(tile.app) },
                onLongClick = { if (!isMoving) actions.appMenu(tile.app, Place.InSection(sectionId)) },
                highlighted = marked,
                showLabel = showLabels,
                modifier = lifted,
            )

            is TileContent.FolderTile -> FolderCard(
                name = tile.name,
                apps = tile.apps,
                onClick = { if (isMoving) actions.stopMoving() else actions.openFolder(tile) },
                onLongClick = { if (!isMoving) actions.folderMenu(sectionId, tile) },
                highlighted = marked,
                modifier = lifted,
            )
        }
        if (isMoving) {
            MoveArrow("◀", Modifier.align(Alignment.CenterStart).offset(x = -ARROW_OUTSIDE))
            MoveArrow("▶", Modifier.align(Alignment.CenterEnd).offset(x = ARROW_OUTSIDE))
        }
    }
}

internal fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    // The key's first word is the kind of item, so a title is reused for a title, a row for a row.
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key.substringBefore(':')) { content() }

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Padding and spacing of the home and "All apps" grids. */
internal val GRID_SIDE = 48.dp
internal val GRID_PADDING = PaddingValues(horizontal = GRID_SIDE, vertical = 32.dp)
internal val GRID_SPACING = 20.dp

/** Tiles per row of a grid. */
internal val TileSize.columns: Int
    get() = when (this) {
        TileSize.Small -> 6
        TileSize.Medium -> 5
        TileSize.Large -> 4
    }

internal val TileSize.rowTileWidth: Dp
    get() = when (this) {
        TileSize.Small -> 176.dp
        TileSize.Medium -> 208.dp
        TileSize.Large -> 256.dp
    }

private const val TILE = "tile"
