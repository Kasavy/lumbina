package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.domain.home.TileNames
import dev.lumbina.tv.core.domain.home.folder
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.MenuEntry
import dev.lumbina.tv.core.ui.TileMenu
import dev.lumbina.tv.core.ui.TileName
import dev.lumbina.tv.core.ui.dialogSurface
import dev.lumbina.tv.core.ui.requestFocusSoon

/** Holding OK on a folder: move, rename, ungroup; opening it comes last, as OK on the tile already does that. */
@Composable
internal fun FolderMenu(
    dialog: HomeDialog.FolderActions,
    commands: HomeCommands,
    menu: MenuState,
    startMoving: (Moving) -> Unit,
) {
    val show = menu::show
    val folder = dialog.folder
    TileMenu(
        title = stringResource(R.string.folder_title, folder.name),
        entries = listOf(
            MenuEntry(stringResource(R.string.menu_move), "◀ ▶") { startMoving(Moving(dialog.sectionId, folder.key)) },
            MenuEntry(stringResource(R.string.folder_rename)) { show(HomeDialog.RenameFolder(folder.id, folder.name)) },
            MenuEntry(stringResource(R.string.folder_dissolve)) { commands.dissolveFolder(folder.id) },
            MenuEntry(stringResource(R.string.menu_open), group = stringResource(R.string.menu_group_folder)) {
                show(HomeDialog.OpenFolder(folder.id))
            },
        ),
        onDismiss = { menu.done(dialog) },
        onBack = menu::back,
        focused = menu.cameBackTo,
        onChoose = menu::chose,
    )
}

@Composable
internal fun OpenFolder(dialog: HomeDialog.OpenFolder, state: HomeUiState, commands: HomeCommands, menu: MenuState) {
    val folder = state.content.folder(dialog.folderId)
    // The folder disappears when its last app leaves it.
    if (folder == null) {
        LaunchedEffect(dialog) { menu.show(null) }
    } else {
        // Names as in the row the folder lies in, and where it has none, on the focused tile:
        // the folder is opened to tell its apps apart.
        val row = state.layout.sections.firstOrNull { section ->
            section.items.any { it is HomeItem.Folder && it.id == dialog.folderId }
        }
        val names = state.appearance.namesIn(HomeRow.ofSection(row?.id ?: HomeSection.FAVORITES).key)
        FolderDialog(
            folder,
            TileName(
                names.takeUnless {
                    it == TileNames.None
                } ?: TileNames.Focused,
                NamesPlace.Under,
            ),
            commands,
            menu,
        )
    }
}

/** An open folder: its apps as tiles; holding OK on one opens its menu. */
@Composable
private fun FolderDialog(
    folder: TileContent.FolderTile,
    names: TileName,
    commands: HomeCommands,
    menu: MenuState,
    modifier: Modifier = Modifier,
) {
    // The window takes no focus of its own: an app gets it, or a press would be needed first. The
    // first, or back from an app's menu, that app.
    val start = remember { FocusRequester() }
    val startAt = folder.apps.indexOfFirst { it.id.flatten() == menu.inFolder }.coerceAtLeast(0)
    // Back: to the menu the folder was opened from, if any; an app's menu opened here comes back here.
    Dialog(onDismissRequest = menu::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val grid = rememberLazyGridState()
        // In the window: its grid is laid out a frame or two after it opens. An app far down is not
        // laid out at all until the grid is brought to it.
        LaunchedEffect(Unit) {
            grid.scrollToItem(startAt)
            start.requestFocusSoon()
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier
                .width(FOLDER_WIDTH)
                .dialogSurface()
                .padding(24.dp),
        ) {
            Text(folder.name, style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                columns = GridCells.Fixed(FOLDER_COLUMNS),
                state = grid,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                itemsIndexed(folder.apps, key = { _, app -> app.id.flatten() }) { index, app ->
                    AppCard(
                        modifier = if (index == startAt) Modifier.focusRequester(start) else Modifier,
                        app = app,
                        onClick = {
                            menu.show(null)
                            commands.open(app)
                        },
                        onLongClick = {
                            menu.inFolder = app.id.flatten()
                            menu.show(HomeDialog.AppActions(app, Place.InFolder(folder.id)))
                        },
                        name = names,
                    )
                }
            }
        }
    }
}

private const val FOLDER_COLUMNS = 4

private val FOLDER_WIDTH = 960.dp
