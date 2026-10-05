package dev.lumbina.tv.feature.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.domain.home.folders
import dev.lumbina.tv.core.domain.home.sectionOfFolder
import dev.lumbina.tv.core.ui.AnchoredMenu
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.MenuEntry
import dev.lumbina.tv.core.ui.NameDialog
import dev.lumbina.tv.core.ui.TileAnchor

/** Where an app tile was when its menu was opened; decides what the menu offers. */
sealed interface Place {
    /** The "all apps" grid. */
    data object All : Place

    data class InSection(val sectionId: String) : Place

    data class InFolder(val folderId: String) : Place
}

/**
 * The dialog shown over home or all apps, the tile it is about ([tile], marked while the dialog
 * is open) and where that tile is on screen ([at]), so menus and the keyboard open under it.
 */
@Stable
internal class MenuState {
    val anchor = TileAnchor()
    var dialog by mutableStateOf<HomeDialog?>(null)
    var tile by mutableStateOf<Moving?>(null)
        private set
    var at by mutableStateOf<Rect?>(null)
        private set

    fun open(next: HomeDialog, about: Moving? = null) {
        tile = about
        at = anchor.bounds
        dialog = next
    }
}

/** The dialog currently shown over the home screen. */
sealed interface HomeDialog {
    data class AppActions(val app: App, val place: Place) : HomeDialog

    data class FolderActions(val sectionId: String, val folder: TileContent.FolderTile) : HomeDialog

    data class OpenFolder(val folderId: String) : HomeDialog

    /** Where the app is shown: a checklist of sections and folders, and new ones. */
    data class WhereToShow(val app: App, val place: Place) : HomeDialog

    data class NameAndBanner(val app: App) : HomeDialog

    /** The rarer actions: app info, uninstall. */
    data class More(val app: App) : HomeDialog

    data class Banner(val app: App) : HomeDialog

    /** Asks for a name. */
    sealed interface Naming : HomeDialog

    data class NewCategory(val app: App) : Naming

    data class NewFolder(val app: App, val sectionId: String) : Naming

    data class RenameApp(val app: App) : Naming

    /** [name]: the voice name [app] has now, empty if none. */
    data class VoiceName(val app: App, val name: String) : Naming

    data class RenameFolder(val folderId: String, val name: String) : Naming
}

/** [anchor]: see [MenuState.at]. */
@Composable
internal fun HomeDialogs(
    dialog: HomeDialog,
    state: HomeUiState,
    commands: HomeCommands,
    show: (HomeDialog?) -> Unit,
    startMoving: (Moving) -> Unit,
    anchor: Rect? = null,
) {
    val dismiss = { show(null) }
    when (dialog) {
        is HomeDialog.AppActions -> AppMenu(dialog, state, commands, show, startMoving, anchor)

        is HomeDialog.FolderActions -> FolderMenu(dialog, commands, show, startMoving, anchor)

        is HomeDialog.OpenFolder -> OpenFolder(dialog, state, commands, show)

        is HomeDialog.WhereToShow -> WhereToShow(dialog, state, commands, show, anchor)

        is HomeDialog.NameAndBanner -> AnchoredMenu(
            title = dialog.app.label,
            entries = listOfNotNull(
                MenuEntry(stringResource(R.string.menu_rename)) { show(HomeDialog.RenameApp(dialog.app)) },
                state.layout.voiceNames[dialog.app.id].orEmpty().takeIf { state.voice }?.let { name ->
                    MenuEntry(stringResource(R.string.menu_voice_name), name.ifEmpty { null }) {
                        show(HomeDialog.VoiceName(dialog.app, name))
                    }
                },
                MenuEntry(stringResource(R.string.menu_banner)) { show(HomeDialog.Banner(dialog.app)) },
            ),
            anchor = anchor,
            onDismiss = dismiss,
        )

        is HomeDialog.More -> MoreMenu(dialog.app, commands, anchor, dismiss)

        is HomeDialog.Naming -> NamingDialog(dialog, commands, anchor, dismiss)

        is HomeDialog.Banner -> BannerDialog(dialog.app, state, commands, dismiss)
    }
}

/**
 * Holding OK on an app: move (in a row), where it is shown, name and banner, hide, and the rare
 * rest under "More…". OK on the tile opens the app, so the menu doesn't offer that.
 */
@Composable
private fun AppMenu(
    dialog: HomeDialog.AppActions,
    state: HomeUiState,
    commands: HomeCommands,
    show: (HomeDialog?) -> Unit,
    startMoving: (Moving) -> Unit,
    anchor: Rect?,
) {
    val app = dialog.app
    val place = dialog.place
    val shownIn = placesOf(app, state)
    val entries = buildList {
        if (place is Place.InSection) {
            add(
                MenuEntry(stringResource(R.string.menu_move), "◀ ▶") {
                    startMoving(Moving(place.sectionId, HomeItem.AppItem(app.id).key))
                },
            )
        }
        add(
            MenuEntry(stringResource(R.string.menu_where), shownIn.ifEmpty { null }) {
                show(HomeDialog.WhereToShow(app, place))
            },
        )
        add(MenuEntry(stringResource(R.string.menu_name_banner)) { show(HomeDialog.NameAndBanner(app)) })
        if (app in state.content.hidden) {
            add(MenuEntry(stringResource(R.string.menu_unhide)) { commands.unhide(app) })
        } else {
            add(MenuEntry(stringResource(R.string.menu_hide)) { commands.hide(app) })
        }
        add(
            MenuEntry(stringResource(R.string.menu_more), stringResource(R.string.menu_more_hint)) {
                show(HomeDialog.More(app))
            },
        )
    }
    AnchoredMenu(title = app.label, entries = entries, anchor = anchor, onDismiss = { show(null) })
}

/** The sections and folders [app] is in, by name: "Favorites, Films". */
@Composable
private fun placesOf(app: App, state: HomeUiState): String {
    val names = state.layout.sections.flatMap { section ->
        section.items.mapNotNull { item ->
            when {
                item is HomeItem.AppItem && item.app == app.id -> section.title()
                item is HomeItem.Folder && app.id in item.apps -> item.name
                else -> null
            }
        }
    }
    return names.joinToString(", ")
}

/**
 * Where [HomeDialog.WhereToShow.app] is shown, ticked: every section and folder. Choosing one puts
 * the app there or takes it out; the list stays open. New folders go in the row the menu came from.
 */
@Composable
private fun WhereToShow(
    dialog: HomeDialog.WhereToShow,
    state: HomeUiState,
    commands: HomeCommands,
    show: (HomeDialog?) -> Unit,
    anchor: Rect?,
) {
    val app = dialog.app
    val folderSection = when (val place = dialog.place) {
        is Place.InSection -> place.sectionId
        is Place.InFolder -> state.layout.sectionOfFolder(place.folderId)?.id
        Place.All -> null
    } ?: HomeSection.FAVORITES
    val entries = buildList {
        state.layout.sections.forEach { section ->
            val inIt = app.id in section.apps
            add(
                MenuEntry(section.title(), if (inIt) "✓" else null) {
                    if (inIt) commands.removeFrom(section.id, app) else commands.addTo(section.id, app)
                    show(dialog)
                },
            )
        }
        state.layout.folders.forEach { folder ->
            val inIt = app.id in folder.apps
            add(
                MenuEntry(stringResource(R.string.where_folder, folder.name), if (inIt) "✓" else null) {
                    if (inIt) commands.removeFromFolder(folder.id, app) else commands.addToFolder(folder.id, app)
                    show(dialog)
                },
            )
        }
        add(MenuEntry(stringResource(R.string.pick_new_folder)) { show(HomeDialog.NewFolder(app, folderSection)) })
        add(MenuEntry(stringResource(R.string.pick_new_category)) { show(HomeDialog.NewCategory(app)) })
    }
    AnchoredMenu(
        title = stringResource(R.string.where_title, app.label),
        entries = entries,
        anchor = anchor,
        onDismiss = { show(null) },
    )
}

@Composable
private fun MoreMenu(app: App, commands: HomeCommands, anchor: Rect?, dismiss: () -> Unit) {
    val context = LocalContext.current
    val unavailable = { Toast.makeText(context, R.string.home_action_unavailable, Toast.LENGTH_SHORT).show() }
    val entries = buildList {
        if (commands.hasDetails) {
            add(MenuEntry(stringResource(R.string.menu_details)) { if (!commands.showDetails(app)) unavailable() })
        }
        add(MenuEntry(stringResource(R.string.menu_uninstall)) { if (!commands.uninstall(app)) unavailable() })
    }
    AnchoredMenu(title = app.label, entries = entries, anchor = anchor, onDismiss = dismiss)
}

/** Naming a new category or folder, or renaming an app or folder. Blank names are ignored, except for apps. */
@Composable
private fun NamingDialog(dialog: HomeDialog.Naming, commands: HomeCommands, anchor: Rect?, dismiss: () -> Unit) {
    when (dialog) {
        is HomeDialog.NewCategory -> Name(
            title = stringResource(R.string.name_category),
            initial = stringResource(R.string.default_category_name),
            onSave = { name -> if (name.isNotBlank()) commands.addToNewCategory(name.trim(), dialog.app) },
            onDismiss = dismiss,
            anchor = anchor,
        )

        is HomeDialog.NewFolder -> Name(
            title = stringResource(R.string.name_folder),
            initial = stringResource(R.string.default_folder_name),
            onSave = { name ->
                if (name.isNotBlank()) commands.createFolder(dialog.sectionId, name.trim(), dialog.app)
            },
            onDismiss = dismiss,
            anchor = anchor,
        )

        is HomeDialog.RenameApp -> Name(
            title = stringResource(R.string.name_app, dialog.app.label),
            initial = dialog.app.label,
            onSave = { commands.rename(dialog.app, it) },
            onDismiss = dismiss,
            anchor = anchor,
            hint = stringResource(R.string.name_app_hint),
        )

        is HomeDialog.VoiceName -> Name(
            title = stringResource(R.string.voice_name_app, dialog.app.label),
            initial = dialog.name,
            onSave = { commands.voiceName(dialog.app, it) },
            onDismiss = dismiss,
            anchor = anchor,
        )

        is HomeDialog.RenameFolder -> Name(
            title = stringResource(R.string.folder_rename_title),
            initial = dialog.name,
            onSave = { name -> if (name.isNotBlank()) commands.renameFolder(dialog.folderId, name.trim()) },
            onDismiss = dismiss,
            anchor = anchor,
        )
    }
}

@Composable
private fun Name(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    anchor: Rect?,
    hint: String? = null,
) = NameDialog(
    title = title,
    initial = initial,
    saveLabel = stringResource(R.string.name_save),
    cancelLabel = stringResource(R.string.name_cancel),
    onSave = onSave,
    onDismiss = onDismiss,
    anchor = anchor,
    hint = hint,
)

@Composable
private fun HomeSection.title(): String = if (isFavorites) stringResource(R.string.home_favorites) else name

/** The user's pictures to choose the app's banner from, and the way back to its own. */
@Composable
private fun BannerDialog(app: App, state: HomeUiState, commands: HomeCommands, dismiss: () -> Unit) {
    val pictures = produceState<List<WallpaperItem>?>(null) { value = commands.bannerChoices() }.value ?: return
    val custom = state.appearance.customBanners[app.id.flatten()]
    val choices = pictures.map { picture ->
        Choice(picture.title.orEmpty()) { commands.setBanner(app, picture.uri) }
    } + listOfNotNull(
        custom?.let { Choice(stringResource(R.string.banner_restore)) { commands.setBanner(app, null) } },
    )
    ChoiceDialog(
        title = stringResource(if (pictures.isEmpty()) R.string.banner_none else R.string.banner_title, app.label),
        choices = choices.ifEmpty { listOf(Choice(stringResource(R.string.banner_how)) {}) },
        onDismiss = dismiss,
    )
}
