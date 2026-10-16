package dev.lumbina.tv.feature.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.ui.MenuEntry
import dev.lumbina.tv.core.ui.NameDialog
import dev.lumbina.tv.core.ui.TICK
import dev.lumbina.tv.core.ui.TileAnchor
import dev.lumbina.tv.core.ui.TileMenu

/** Where an app tile was when its menu was opened; decides what the menu offers. */
sealed interface Place {
    /** The "all apps" grid. */
    data object All : Place

    data class InSection(val sectionId: String) : Place

    data class InFolder(val folderId: String) : Place
}

/**
 * The dialog shown over home or all apps, the tile it is about ([tile], marked while the dialog
 * is open) and where that tile is on screen ([at]), so the keyboard that names it opens under it.
 * A page one leads to goes over it, and Back comes back to it, as in the settings.
 */
@Stable
internal class MenuState {
    val anchor = TileAnchor()
    var dialog by mutableStateOf<HomeDialog?>(null)
        private set
    var tile by mutableStateOf<Moving?>(null)
        private set
    var at by mutableStateOf<Rect?>(null)
        private set

    /** The pages the shown one was reached from, the nearest last, each with the entry that led on. */
    private val trail = mutableStateListOf<Pair<HomeDialog, String?>>()

    /** The entry of the shown menu chosen last. */
    private var chosen: String? = null

    /** On a page come back to: the entry that led on from it, for the focus to go back to. */
    var cameBackTo by mutableStateOf<String?>(null)
        private set

    fun open(next: HomeDialog, about: Moving? = null) {
        tile = about
        at = anchor.bounds
        trail.clear()
        chosen = null
        cameBackTo = null
        dialog = next
    }

    /** [entry] of the shown menu was chosen. */
    fun chose(entry: String) {
        chosen = entry
    }

    /** [next] over the one shown, which Back then comes back to; null closes them all. */
    fun show(next: HomeDialog?) {
        val now = dialog
        if (next == null) {
            trail.clear()
        } else if (now != null && now != next) {
            trail += now to chosen
        }
        chosen = null
        cameBackTo = null
        dialog = next
    }

    /** Back: to the page the shown one was reached from; from the first, closed. */
    fun back() {
        val (page, entry) = trail.removeLastOrNull() ?: (null to null)
        chosen = null
        cameBackTo = entry
        dialog = page
    }

    /** What [shown] was for is done: everything closes, unless it has led on to another page. */
    fun done(shown: HomeDialog) {
        if (dialog == shown) show(null)
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

    /** The menu of the shortcut to all apps. */
    data object ShortcutActions : HomeDialog

    /** The apps to tick for one section, opened by its "+" tile. */
    data class PickApps(val sectionId: String) : HomeDialog

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

/** [anchor]: see [MenuState.at]; for the dialogs that name the tile. */
@Composable
internal fun HomeDialogs(
    dialog: HomeDialog,
    state: HomeUiState,
    commands: HomeCommands,
    menu: MenuState,
    startMoving: (Moving) -> Unit,
    anchor: Rect? = null,
) {
    val show = menu::show
    val back = menu::back
    // An entry chosen: done, unless it led on to a page of its own.
    val done = { menu.done(dialog) }
    when (dialog) {
        is HomeDialog.AppActions -> AppMenu(dialog, state, commands, menu, startMoving)

        is HomeDialog.FolderActions -> FolderMenu(dialog, commands, menu, startMoving)

        is HomeDialog.OpenFolder -> OpenFolder(dialog, state, commands, menu)

        is HomeDialog.WhereToShow -> WhereToShow(dialog, state, commands, menu)

        is HomeDialog.NameAndBanner -> TileMenu(
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
            onDismiss = done,
            onBack = back,
            focused = menu.cameBackTo,
            onChoose = menu::chose,
        )

        is HomeDialog.Naming -> NamingDialog(dialog, state, commands, anchor, back)

        is HomeDialog.Banner ->
            BannerPicker(dialog.app, state.appearance.customBanners[dialog.app.id.flatten()], commands, back)

        is HomeDialog.PickApps -> PickApps(dialog, state, commands, back)

        HomeDialog.ShortcutActions -> TileMenu(
            title = stringResource(R.string.home_all_apps),
            entries = listOf(
                MenuEntry(stringResource(R.string.menu_move), "◀ ▶") {
                    startMoving(Moving(HomeSection.FAVORITES, HomeItem.AllApps.key))
                },
                MenuEntry(stringResource(R.string.menu_out_of_favorites)) { commands.removeShortcut() },
            ),
            onDismiss = done,
            onBack = back,
            subtitle = stringResource(R.string.shortcut_hint),
        )
    }
}

/**
 * Holding OK on an app. What changes the screen comes first, with the focus on it: move (in a row),
 * into or out of the favorites, out of this category, where it is shown, name and banner, hide.
 * Then the app itself: open, its details, uninstall. Opening is not first: OK on the tile does that.
 */
@Composable
private fun AppMenu(
    dialog: HomeDialog.AppActions,
    state: HomeUiState,
    commands: HomeCommands,
    menu: MenuState,
    startMoving: (Moving) -> Unit,
) {
    val show = menu::show
    val app = dialog.app
    val place = dialog.place
    val shownIn = placesOf(app, state)
    val hidden = app in state.content.hidden
    // In the row itself: an app inside a folder there is taken out through "Where to show".
    val inFavorites = state.layout.favorites.items.any { it is HomeItem.AppItem && it.app == app.id }
    val category = (place as? Place.InSection)?.let {
        state.layout.section(it.sectionId)
    }?.takeUnless { it.isFavorites }
    val entries = buildList {
        if (place is Place.InSection) {
            add(
                MenuEntry(stringResource(R.string.menu_move), "◀ ▶") {
                    startMoving(Moving(place.sectionId, HomeItem.AppItem(app.id).key))
                },
            )
        }
        if (inFavorites) {
            add(
                MenuEntry(stringResource(R.string.menu_out_of_favorites)) {
                    commands.takeOut(HomeSection.FAVORITES, app)
                },
            )
        } else if (!hidden) {
            add(MenuEntry(stringResource(R.string.menu_to_favorites)) { commands.addTo(HomeSection.FAVORITES, app) })
        }
        category?.let { section ->
            add(MenuEntry(stringResource(R.string.menu_out_of, section.name)) { commands.takeOut(section.id, app) })
        }
        if (!hidden) {
            add(
                MenuEntry(stringResource(R.string.menu_where), shownIn.ifEmpty { null }) {
                    show(HomeDialog.WhereToShow(app, place))
                },
            )
        }
        add(MenuEntry(stringResource(R.string.menu_name_banner)) { show(HomeDialog.NameAndBanner(app)) })
        if (hidden) {
            add(MenuEntry(stringResource(R.string.menu_unhide)) { commands.unhide(app) })
        } else {
            add(MenuEntry(stringResource(R.string.menu_hide)) { commands.hide(app) })
        }
        addAll(ownActions(app, commands))
    }
    TileMenu(
        title = app.label,
        entries = entries,
        onDismiss = { menu.done(dialog) },
        onBack = menu::back,
        subtitle = shownIn.ifEmpty { stringResource(R.string.menu_only_all_apps) },
        focused = menu.cameBackTo,
        onChoose = menu::chose,
    )
}

/** The app's own actions, under their caption: open, its details where the device shows them, uninstall. */
@Composable
private fun ownActions(app: App, commands: HomeCommands): List<MenuEntry> {
    val context = LocalContext.current
    val unavailable = { Toast.makeText(context, R.string.home_action_unavailable, Toast.LENGTH_SHORT).show() }
    val appGroup = stringResource(R.string.menu_group_app)
    return buildList {
        add(MenuEntry(stringResource(R.string.menu_open), group = appGroup) { commands.open(app) })
        if (commands.hasDetails) {
            add(
                MenuEntry(stringResource(R.string.menu_details), group = appGroup) {
                    if (!commands.showDetails(app)) unavailable()
                },
            )
        }
        add(
            MenuEntry(stringResource(R.string.menu_uninstall), group = appGroup) {
                if (!commands.uninstall(app)) unavailable()
            },
        )
    }
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
 * Where [HomeDialog.WhereToShow.app] is shown, ticked: every row, and under each its folders and
 * "New folder…", which are always there so that ticking a row doesn't shift the list under the
 * focus. Choosing one puts the app there or takes it out; the list stays open.
 */
@Composable
private fun WhereToShow(dialog: HomeDialog.WhereToShow, state: HomeUiState, commands: HomeCommands, menu: MenuState) {
    val show = menu::show
    val app = dialog.app
    val entries = buildList {
        state.layout.sections.forEach { section ->
            // In the row itself; inside one of its folders is the folder's own tick.
            val inIt = section.items.any { it is HomeItem.AppItem && it.app == app.id }
            add(
                MenuEntry(section.title(), if (inIt) TICK else null, staysOpen = true) {
                    if (inIt) commands.removeFrom(section.id, app) else commands.addTo(section.id, app)
                },
            )
            section.items.filterIsInstance<HomeItem.Folder>().forEach { folder ->
                val inFolder = app.id in folder.apps
                val name = stringResource(R.string.where_folder, folder.name)
                add(
                    MenuEntry(name, if (inFolder) TICK else null, nested = true, staysOpen = true) {
                        if (inFolder) {
                            commands.removeFromFolder(folder.id, app)
                        } else {
                            commands.addToFolder(folder.id, app)
                        }
                    },
                )
            }
            add(
                MenuEntry(stringResource(R.string.pick_new_folder), nested = true) {
                    show(HomeDialog.NewFolder(app, section.id))
                },
            )
        }
        add(MenuEntry(stringResource(R.string.pick_new_category)) { show(HomeDialog.NewCategory(app)) })
    }
    TileMenu(
        title = stringResource(R.string.where_heading),
        entries = entries,
        onDismiss = { menu.done(dialog) },
        onBack = menu::back,
        subtitle = stringResource(R.string.where_hint),
        focused = menu.cameBackTo,
        onChoose = menu::chose,
        path = listOf(app.label),
    )
}

/** Naming a new category or folder, or renaming an app or folder. Blank names are ignored, except for apps. */
@Composable
private fun NamingDialog(
    dialog: HomeDialog.Naming,
    state: HomeUiState,
    commands: HomeCommands,
    anchor: Rect?,
    dismiss: () -> Unit,
) {
    when (dialog) {
        is HomeDialog.NewCategory -> PresetOrName(
            taken = state.layout.categories.map { it.name },
            title = stringResource(R.string.name_category),
            initial = stringResource(R.string.default_category_name),
            onSave = { name -> if (name.isNotBlank()) commands.addToNewCategory(name.trim(), dialog.app) },
            onDismiss = dismiss,
            anchor = anchor,
        )

        is HomeDialog.NewFolder -> PresetOrName(
            taken = state.layout.sections.flatMap { it.items }.filterIsInstance<HomeItem.Folder>().map { it.name },
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
            needsText = true,
        )
    }
}

/**
 * A name for something new: first the ready ones not taken yet, one press each, and "My own…",
 * which leads to the keyboard. With every ready name taken, the keyboard at once.
 */
@Composable
private fun PresetOrName(
    taken: List<String>,
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    anchor: Rect?,
) {
    val free = stringArrayResource(R.array.ready_names).filter { name ->
        taken.none { it.equals(name, ignoreCase = true) }
    }
    var typing by remember { mutableStateOf(free.isEmpty()) }
    if (typing) {
        Name(title, initial, onSave, onDismiss, anchor, needsText = true)
        return
    }
    val entries = free.map { name -> MenuEntry(name) { onSave(name) } } +
        MenuEntry(stringResource(R.string.name_own), staysOpen = true) { typing = true }
    TileMenu(title = title, entries = entries, onDismiss = onDismiss)
}

@Composable
private fun Name(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    anchor: Rect?,
    hint: String? = null,
    needsText: Boolean = false,
) = NameDialog(
    title = title,
    initial = initial,
    saveLabel = stringResource(R.string.name_save),
    cancelLabel = stringResource(R.string.name_cancel),
    onSave = onSave,
    onDismiss = onDismiss,
    anchor = anchor,
    hint = hint,
    needsText = needsText,
)

@Composable
private fun HomeSection.title(): String = if (isFavorites) stringResource(R.string.home_favorites) else name
