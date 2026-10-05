package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.domain.home.folder
import dev.lumbina.tv.core.ui.AnchoredMenu
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.MenuEntry

/** Holding OK on a folder: move, rename, ungroup (OK already opens it). */
@Composable
internal fun FolderMenu(
    dialog: HomeDialog.FolderActions,
    commands: HomeCommands,
    show: (HomeDialog?) -> Unit,
    startMoving: (Moving) -> Unit,
    anchor: Rect?,
) {
    val folder = dialog.folder
    AnchoredMenu(
        title = stringResource(R.string.folder_title, folder.name),
        entries = listOf(
            MenuEntry(stringResource(R.string.menu_move), "◀ ▶") { startMoving(Moving(dialog.sectionId, folder.key)) },
            MenuEntry(stringResource(R.string.folder_rename)) { show(HomeDialog.RenameFolder(folder.id, folder.name)) },
            MenuEntry(stringResource(R.string.folder_dissolve), stringResource(R.string.folder_dissolve_hint)) {
                commands.dissolveFolder(folder.id)
            },
        ),
        anchor = anchor,
        onDismiss = { show(null) },
    )
}

@Composable
internal fun OpenFolder(
    dialog: HomeDialog.OpenFolder,
    state: HomeUiState,
    commands: HomeCommands,
    show: (HomeDialog?) -> Unit,
) {
    val folder = state.content.folder(dialog.folderId)
    // The folder disappears when its last app leaves it.
    if (folder == null) {
        LaunchedEffect(dialog, show) { show(null) }
    } else {
        FolderDialog(folder, state.appearance.showLabels, commands, show)
    }
}

/** An open folder: its apps as tiles; holding OK on one opens its menu. */
@Composable
private fun FolderDialog(
    folder: TileContent.FolderTile,
    showLabels: Boolean,
    commands: HomeCommands,
    show: (HomeDialog?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = { show(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier
                .width(FOLDER_WIDTH)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Text(folder.name, style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                columns = GridCells.Fixed(FOLDER_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(folder.apps, key = { it.id.flatten() }) { app ->
                    AppCard(
                        app = app,
                        onClick = {
                            show(null)
                            commands.open(app)
                        },
                        onLongClick = { show(HomeDialog.AppActions(app, Place.InFolder(folder.id))) },
                        showLabel = showLabels,
                    )
                }
            }
        }
    }
}

private const val FOLDER_COLUMNS = 4
private val FOLDER_WIDTH = 960.dp
