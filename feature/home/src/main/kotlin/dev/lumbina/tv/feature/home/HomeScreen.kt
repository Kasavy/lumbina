package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.SectionContent
import dev.lumbina.tv.core.domain.home.SectionStyle
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.FolderCard
import dev.lumbina.tv.core.ui.PanelHost
import dev.lumbina.tv.core.ui.TileName
import dev.lumbina.tv.core.ui.anchorWhenFocused
import dev.lumbina.tv.core.ui.preloadArtwork

// The dialogs only see the HomeCommands interface, not the view model's state.
@Suppress("ViewModelForwarding", "LongMethod")
@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    refocus: Int = 0,
    toAllApps: Int = 0,
    visible: Boolean = true,
    over: ScreenOnTop? = null,
    header: @Composable () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    channelsViewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels by channelsViewModel.state.collectAsStateWithLifecycle()
    val hint by viewModel.holdOkHint.collectAsStateWithLifecycle()
    val failedApp by viewModel.failed.collectAsStateWithLifecycle()
    val failedProgram by channelsViewModel.failed.collectAsStateWithLifecycle()
    val spotlight by viewModel.spotlight.collectAsStateWithLifecycle()
    val movingTile = remember { MovingTile() }
    var moving by movingTile::tile
    val allApps = rememberAllApps(toAllApps, refocus, visible)
    // Back on "All apps": home takes the focus back while still hidden under it, then it goes.
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(allApps.value) { closing = false }
    val homeShown = visible && (!allApps.value || closing)
    HomeComings(
        refocus = refocus,
        visible = visible,
        shown = {
            viewModel.onHomeShown()
            channelsViewModel.onHomeShown()
        },
    ) { moving = null }
    val menu = remember { MenuState() }
    // A value of its own: the state read through its delegate could not be smart-cast.
    val current = state
    val actions = rememberHomeActions(viewModel, channelsViewModel, menu, movingTile, current) { allApps.value = true }
    // The tile may go from under a move (its app uninstalled): then there is nothing to move.
    val lost = moving?.let { current != null && current.indexOf(it) < 0 } == true
    LaunchedEffect(lost) { if (lost) moving = null }
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    var undone by remember { mutableIntStateOf(0) }
    val onUndo = { if (viewModel.undoLast()) undone++ }
    val failed = newerOf(failedApp, failedProgram)
    // One message at a time at the bottom: the move's, a failure, then "Undo", then the hint.
    val offered = undo.takeIf { moving == null && failed == null }
    // "All apps" is inside too, so what is hidden there can be undone there.
    UndoHost(offered, onUndo, modifier.hiddenFromFocus(!visible, over), busy = moving != null) {
        Box(Modifier.fillMaxSize().hiddenFromFocus(!homeShown, null)) {
            HomeScreen(
                state = current,
                channels = channels,
                moving = moving,
                menuTile = menu.tile.takeIf { menu.dialog != null },
                actions = actions,
                spotlight = spotlight.takeIf { !visible },
                refocus = refocus,
                visible = homeShown,
                undo = offered,
                undone = undone,
                header = header,
                // Under the "All apps" screen home is not drawn: the wallpaper is its background.
                modifier = Modifier.graphicsLayer { alpha = if (allApps.value) 0f else 1f },
            )
        }
        if (current != null && allApps.value && visible) {
            AllAppsScreen(
                state = current,
                actions = actions,
                onAppsOrder = viewModel::setAppsOrder,
                closing = closing,
                onClose = { closing = true },
                onFinish = { allApps.value = false },
                undone = undone,
            )
        }
        val moved = current?.nameOf(moving, stringResource(R.string.home_all_apps))
        HomeHints(hint, moved, Modifier.align(Alignment.BottomCenter), failed)
    }
    val shown = menu.dialog
    // One window as a menu leads to the next page, so home does not show between them.
    PanelHost {
        if (current != null && shown != null) {
            HomeDialogs(
                dialog = shown,
                state = current,
                commands = viewModel,
                menu = menu,
                startMoving = { moving = it.copy(from = current.indexOf(it)) },
                anchor = menu.at,
            )
        }
    }
}

/** Where [tile] is in its section now; -1 if it isn't there. */
internal fun HomeUiState.indexOf(tile: Moving): Int =
    content.sections.firstOrNull { it.id == tile.sectionId }?.tiles?.indexOfFirst { it.key == tile.key } ?: -1

/** A tile being moved with ◀ ▶ (or whose menu is open): its section, item key and where it started. */
data class Moving(val sectionId: String, val key: String, val from: Int = -1) {
    fun isTile(sectionId: String, key: String): Boolean = this.sectionId == sectionId && this.key == key
}

/**
 * The rows in the user's order: Favorites, continue watching, recently installed, categories, the
 * channels' recommendations, the first apps. Holding OK on a tile opens its menu. While the
 * settings work on a row ([spotlight]), it is brought up and stays lit, the rest dimmed.
 */
@Suppress("LongParameterList")
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
    spotlight: String? = null,
    undo: Undo? = null,
    undone: Int = 0,
    header: @Composable () -> Unit = {},
) {
    val last = rememberSaveable { mutableStateOf<FocusKey?>(null) }
    val focus = remember { HomeFocus(last) }
    val grid = rememberHomeGrid()
    val apps = remember(state) { state?.let(::homeApps).orEmpty() }
    // Every app's picture loaded while home is looked at, so "All apps" and the rows scroll from cache.
    val context = LocalContext.current
    val installed = state?.content?.apps
    LaunchedEffect(installed) { installed?.let { preloadArtwork(context, it) } }
    val shown = remember(state, channels, apps) { HomeRows(state, channels, apps) }
    RestoreFocus(focus, state, channels, grid, refocus, visible, apps)
    // The settings are working on a row: it is shown here as it changes.
    LaunchedEffect(spotlight) { spotlight?.let { grid.showRow(it) } }
    FocusOnUndone(focus, menuTile != null, undo, undone)
    FocusOnNeighbour(focus, shown)
    HomeBack(visible && moving == null, grid, focus, shown)
    Box(modifier) {
        CompositionLocalProvider(LocalHomeFocus provides focus) {
            HomeGrid(shown, Marks(moving, menuTile), actions, grid, header, Modifier.then(focus.area))
        }
        if (spotlight != null) SpotlightShade(grid, spotlight)
    }
}

internal fun LazyGridScope.section(
    section: SectionContent,
    key: String,
    look: HomeAppearance,
    marks: Marks,
    actions: HomeActions,
) {
    val row = RowLookUi.of(look, key)
    if (look.showTitleIn(key)) {
        fullWidth("title:${section.id}") {
            val title = if (section.isFavorites) stringResource(R.string.home_favorites) else section.name
            SectionTitle(title)
        }
    }
    if (section.tiles.isEmpty()) {
        fullWidth("empty:${section.id}") {
            val hint = if (section.isFavorites) R.string.home_favorites_empty else R.string.home_category_empty
            Hint(stringResource(hint))
        }
    }
    val movingHere = marks.moving?.sectionId == section.id
    // What every row of apps ends with; it is not moved.
    // An empty row keeps its "+" whatever the setting: its hint says to press it.
    val plus = look.showPlusIn(key) || section.tiles.isEmpty()
    val trailing = buildList {
        if (plus) add(Trailing(PLUS_KEY) { PlusTile({ actions.pickApps(section.id) }, it) })
    }.map { tile ->
        Trailing(tile.key) { modifier ->
            val focus = FocusKey(section.id, tile.key)
            tile.content(modifier.dimmedIf(movingHere).homeFocus(LocalHomeFocus.current, focus))
        }
    }
    val tile: @Composable (TileContent, Modifier) -> Unit = { content, modifier ->
        SectionTile(
            section.id,
            content,
            row.name,
            marks,
            actions,
            modifier.homeFocus(LocalHomeFocus.current, FocusKey(section.id, content.key)),
        )
    }
    when {
        section.style == SectionStyle.Grid && !row.plate -> {
            val span = GridItemSpan(row.size.gridSpan)
            items(section.tiles, key = { "${section.id}:${it.key}" }, span = { span }, contentType = { TILE }) {
                tile(it, Modifier.gridCell())
            }
            items(trailing, key = { "${section.id}:${it.key}" }, span = { span }, contentType = { it.key }) {
                it.content(Modifier.gridCell())
            }
        }

        // A grid on its backing is one item: the backing goes round all its lines.
        section.style == SectionStyle.Grid -> fullWidth("row:${section.id}") {
            PlateGrid(section, row.size.columns, trailing, tile)
        }

        else -> fullWidth("row:${section.id}") {
            val width = row.size.appTileWidth(inset = if (row.plate) PLATE_INSET else 0.dp)
            SectionRow(section, width, marks.moving, plate = row.plate, trailing = trailing, tile = tile)
        }
    }
}

@Composable
private fun SectionRow(
    section: SectionContent,
    tileWidth: Dp,
    moving: Moving?,
    modifier: Modifier = Modifier,
    plate: Boolean = false,
    trailing: List<Trailing> = emptyList(),
    tile: @Composable (TileContent, Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val movingIndex = section.tiles.indexOfFirst { moving?.isTile(section.id, it.key) == true }
    LaunchedEffect(movingIndex) { if (movingIndex >= 0) listState.animateScrollToItem(movingIndex) }
    RowStart(section.id, listState)
    // Apps ticked from the "+" push it to the right: it stays in view, the focus is on it.
    val focus = LocalHomeFocus.current
    val count = section.tiles.size + trailing.size
    // Read in the effect, not while composing: every row would recompose on every move of the focus.
    LaunchedEffect(count) { if (focus?.last == FocusKey(section.id, PLUS_KEY)) listState.scrollToItem(count - 1) }
    val tiles: LazyListScope.() -> Unit = {
        items(section.tiles, key = { it.key }) { tile(it, Modifier.width(tileWidth)) }
        items(trailing, key = { it.key }) { it.content(Modifier.width(tileWidth)) }
    }
    if (plate) PlateRow(modifier, listState, tiles) else HomeRow(modifier, listState, tiles)
}

/** A section laid out as a grid on its backing: every line of it inside one backing, [columns] tiles a line. */
@Composable
private fun PlateGrid(
    section: SectionContent,
    columns: Int,
    trailing: List<Trailing>,
    tile: @Composable (TileContent, Modifier) -> Unit,
) = Plate {
    BoxWithConstraints {
        val width = with(LocalDensity.current) {
            Modifier.width(tileWidthPx(constraints.maxWidth, GRID_SPACING.roundToPx(), columns).toDp())
        }
        // One layout for all the lines, each tile keyed: a tile being moved keeps its focus as it
        // changes places, onto another line too.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
            maxItemsInEachRow = columns,
        ) {
            section.tiles.forEach { shown -> key(shown.key) { tile(shown, width) } }
            trailing.forEach { extra -> key(extra.key) { extra.content(width) } }
        }
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
    name: TileName,
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
            .moveKeys(isMoving, actions),
    ) {
        val lifted = if (isMoving) Modifier.lifted() else Modifier
        when (tile) {
            is TileContent.AppTile -> AppCard(
                app = tile.app,
                banner = actions.banner(tile.app),
                onClick = { if (isMoving) actions.stopMoving() else actions.open(tile.app) },
                onLongClick = { if (!isMoving) actions.appMenu(tile.app, Place.InSection(sectionId)) },
                highlighted = marked,
                name = name,
                modifier = lifted,
            )

            is TileContent.FolderTile -> FolderCard(
                name = tile.name,
                apps = tile.apps,
                onClick = { if (isMoving) actions.stopMoving() else actions.openFolder(tile) },
                onLongClick = { if (!isMoving) actions.folderMenu(sectionId, tile) },
                highlighted = marked,
                names = name,
                modifier = lifted,
            )

            TileContent.AllAppsTile -> AllAppsTile(
                onClick = { if (isMoving) actions.stopMoving() else actions.openAllApps() },
                onLongClick = { if (!isMoving) actions.shortcutMenu() },
                label = stringResource(R.string.home_all_apps),
                name = name,
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
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key.substringBefore(':')) {
        Box(Modifier.gridCell()) { content() }
    }

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    // A soft shadow in the veil's color: a heading stands on the picture itself, above the dense veil.
    val veil = LocalTheme.current.colorOf(ThemeRole.Veil)
    val base = MaterialTheme.typography.titleMedium
    val style = remember(veil, base) {
        base.copy(shadow = Shadow(veil.copy(alpha = TITLE_SHADOW_ALPHA), blurRadius = 8f))
    }
    Text(
        text = text,
        style = style,
        modifier = modifier.padding(top = 8.dp),
    )
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

/**
 * [shown] each time home comes up. [left] when it is asked for anew (the Home key takes the focus
 * off where it was), covered by another screen, or left for an app: a tile's move left unfinished
 * ends where the tile stands.
 */
@Composable
private fun HomeComings(refocus: Int, visible: Boolean, shown: () -> Unit, left: () -> Unit) {
    val onShown by rememberUpdatedState(shown)
    val onLeft by rememberUpdatedState(left)
    LaunchedEffect(refocus, visible) {
        onLeft()
        if (visible) onShown()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onLeft() }
}

/** A tile a row ends with after its apps: the "+", the tile that opens all apps. */
internal class Trailing(val key: String, val content: @Composable (Modifier) -> Unit)

private const val TILE = "tile"
private const val TITLE_SHADOW_ALPHA = 0.7f
