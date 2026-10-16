package dev.lumbina.tv.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.ui.TileAnchor

/** What the home screen can ask for; grouped to keep signatures short. */
class HomeActions(
    val open: (App) -> Unit,
    val openFolder: (TileContent.FolderTile) -> Unit,
    val appMenu: (App, Place) -> Unit,
    val folderMenu: (String, TileContent.FolderTile) -> Unit,
    /** Holding OK on the tile that opens all apps, in Favorites. */
    val shortcutMenu: () -> Unit,
    /** The "All apps" screen: from its tile in Favorites, and the last tile of "Apps". */
    val openAllApps: () -> Unit,
    /** The "+" at the end of a row: the apps to tick for the section with this id. */
    val pickApps: (String) -> Unit,
    val move: (Int) -> Unit,
    /** OK: the tile stays where it is now. */
    val stopMoving: () -> Unit,
    /** Back: the tile goes back where it was. */
    val cancelMoving: () -> Unit,
    val openProgram: (ProgramCard) -> Unit,
    /** The user answered the request for access to the channels: granted or not. */
    val channelsAccess: (Boolean) -> Unit,
    /** Switches a row off; the second is its title, for saying so. */
    val hideRow: (HomeRow, String) -> Unit,
    /** The user's own banner for an app, if any. */
    val banner: (App) -> String?,
    /** Tiles report here while focused, so the keyboard that names one can open under it. */
    val anchor: TileAnchor,
)

/** The tile being moved now, if any. */
@Stable
internal class MovingTile {
    var tile by mutableStateOf<Moving?>(null)
}

/** One instance for the screen's life, so tiles taking it can skip recomposing when home does. */
@Suppress("ViewModelForwarding") // only its commands are called
@Composable
internal fun rememberHomeActions(
    viewModel: HomeViewModel,
    channelsViewModel: ChannelsViewModel,
    menu: MenuState,
    moving: MovingTile,
    state: HomeUiState?,
    openAllApps: () -> Unit,
): HomeActions {
    val latest by rememberUpdatedState(state)
    return remember(viewModel, channelsViewModel) {
        HomeActions(
            open = viewModel::open,
            openFolder = { menu.open(HomeDialog.OpenFolder(it.id)) },
            appMenu = { app, place ->
                viewModel.onMenuOpened()
                val section = (place as? Place.InSection)?.sectionId.orEmpty()
                menu.open(HomeDialog.AppActions(app, place), Moving(section, HomeItem.AppItem(app.id).key))
            },
            folderMenu = { sectionId, folder ->
                viewModel.onMenuOpened()
                menu.open(HomeDialog.FolderActions(sectionId, folder), Moving(sectionId, folder.key))
            },
            shortcutMenu = {
                viewModel.onMenuOpened()
                menu.open(HomeDialog.ShortcutActions, Moving(HomeSection.FAVORITES, HomeItem.AllApps.key))
            },
            openAllApps = openAllApps,
            pickApps = { menu.open(HomeDialog.PickApps(it)) },
            move = { by -> moving.tile?.let { viewModel.moveItem(it.sectionId, it.key, by) } },
            stopMoving = { moving.tile = null },
            cancelMoving = {
                moving.tile?.let(viewModel::putBack)
                moving.tile = null
            },
            openProgram = channelsViewModel::open,
            channelsAccess = channelsViewModel::onAccess,
            hideRow = viewModel::hideRow,
            banner = { app -> latest?.appearance?.customBanners?.get(app.id.flatten()) },
            anchor = menu.anchor,
        )
    }
}
