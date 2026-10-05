package dev.lumbina.tv.core.domain.home

import dev.lumbina.tv.core.domain.app.AppId

/**
 * Puts [app] into a new folder in a section. The app first becomes a tile of its own there if it
 * isn't one (it is in another folder of the section, or not in it at all).
 */
fun HomeLayout.createFolderWith(sectionId: String, folderId: String, name: String, app: AppId): HomeLayout {
    val alone = section(sectionId)?.items?.contains(HomeItem.AppItem(app)) == true
    val placed = if (alone) this else remove(sectionId, app).add(sectionId, app)
    return placed.createFolder(sectionId, folderId, name, app)
}

/** Every folder, section by section. */
val HomeLayout.folders: List<HomeItem.Folder>
    get() = sections.flatMap { it.items }.filterIsInstance<HomeItem.Folder>()

/** The section holding the folder with [folderId]. */
fun HomeLayout.sectionOfFolder(folderId: String): HomeSection? =
    sections.firstOrNull { section -> section.items.any { it is HomeItem.Folder && it.id == folderId } }

/** The folder with [id] as shown, with its installed apps. */
fun HomeContent.folder(id: String): TileContent.FolderTile? =
    sections.flatMap { it.tiles }.filterIsInstance<TileContent.FolderTile>().firstOrNull { it.id == id }
