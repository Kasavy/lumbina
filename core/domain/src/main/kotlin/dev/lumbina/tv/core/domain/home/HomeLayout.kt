package dev.lumbina.tv.core.domain.home

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.InputSource
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** Something placed in a section: an app, a folder of apps, the shortcut to all apps or a video input. */
sealed interface HomeItem {
    /** Identifies the item within its section, e.g. for moving it. */
    val key: String

    data class AppItem(val app: AppId) : HomeItem {
        override val key: String get() = "app:${app.packageName}/${app.activityName}"
    }

    data class Folder(val id: String, val name: String, val apps: List<AppId>) : HomeItem {
        override val key: String get() = "folder:$id"
    }

    /**
     * The tile that opens the "All apps" screen. Any row may have it, once: the user puts it in,
     * moves it like a tile and takes it out; the "Apps" row's "See all" opens the screen as well.
     */
    data object AllApps : HomeItem {
        override val key: String = "all-apps"
    }

    /**
     * A tile that switches to the device's video input [id] ([InputSource.id]). Any row may have
     * each input once, as [AllApps]; one the device lacks is not shown.
     */
    data class Input(val id: String) : HomeItem {
        override val key: String get() = "input:$id"
    }
}

/**
 * How a section lays its tiles out: a [Row] that scrolls sideways or a [Grid] of several lines.
 * Whether it stands on a backing is the row's look ([HomeAppearance.plateIn]).
 */
enum class SectionStyle { Row, Grid }

/** A row (or grid) on the home screen: the favorites, or a category the user made. */
data class HomeSection(
    val id: String,
    val name: String,
    val style: SectionStyle = SectionStyle.Row,
    val items: List<HomeItem> = emptyList(),
) {
    val isFavorites: Boolean get() = id == FAVORITES

    /** Every app in the section, including those inside folders. */
    val apps: List<AppId> get() = items.flatMap { item -> item.apps() }

    companion object {
        /** The built-in first section. Its name is shown localized, so it is stored empty. */
        const val FAVORITES = "favorites"

        /** Favorites as a new installation has it: no apps yet, only the tile that opens all apps. */
        fun emptyFavorites(): HomeSection = HomeSection(FAVORITES, "", SectionStyle.Row, listOf(HomeItem.AllApps))
    }
}

private fun HomeItem.apps(): List<AppId> = when (this) {
    is HomeItem.AppItem -> listOf(app)
    is HomeItem.Folder -> apps
    HomeItem.AllApps, is HomeItem.Input -> emptyList()
}

/**
 * How the user arranged the home screen: sections (favorites first, then categories), apps
 * they hid and names they gave apps. Invariants: favorites come first; section and folder ids
 * are unique; an app appears at most once per section; folders are never empty; a hidden app
 * is in no section. Entries may refer to apps that are not installed (anymore); [arrange]
 * skips them, so reinstalling brings them back.
 */
@Suppress("TooManyFunctions")
data class HomeLayout(
    /** By default an empty Favorites; a new installation starts with [HomeSection.emptyFavorites]. */
    val sections: List<HomeSection> = listOf(HomeSection(HomeSection.FAVORITES, "")),
    val hidden: Set<AppId> = emptySet(),
    val labels: Map<AppId, String> = emptyMap(),
    /** Names to call apps by in voice commands, besides the ones on screen. */
    val voiceNames: Map<AppId, String> = emptyMap(),
    /** Names the user gave video inputs, by [InputSource.id], instead of the firmware's. */
    val inputLabels: Map<String, String> = emptyMap(),
) {
    init {
        require(sections.firstOrNull()?.isFavorites == true) { "favorites must come first" }
        require(sections.map { it.id }.distinct().size == sections.size) { "duplicate section ids" }
        val folders = sections.flatMap { it.items }.filterIsInstance<HomeItem.Folder>()
        require(folders.map { it.id }.distinct().size == folders.size) { "duplicate folder ids" }
        require(folders.none { it.apps.isEmpty() }) { "empty folder" }
        sections.forEach { section ->
            require(section.items.count { it == HomeItem.AllApps } <= 1) { "two all-apps tiles in ${section.id}" }
            val inputs = section.items.filterIsInstance<HomeItem.Input>()
            require(inputs.distinct().size == inputs.size) { "duplicate input in ${section.id}" }
            require(section.apps.distinct().size == section.apps.size) { "duplicate app in ${section.id}" }
            require(section.apps.none { it in hidden }) { "hidden app in ${section.id}" }
        }
    }

    val favorites: HomeSection get() = sections.first()

    val categories: List<HomeSection> get() = sections.drop(1)

    fun section(id: String): HomeSection? = sections.firstOrNull { it.id == id }

    // --- Sections ---

    fun addCategory(id: String, name: String): HomeLayout {
        require(section(id) == null) { "section $id exists" }
        return copy(sections = sections + HomeSection(id, name))
    }

    fun renameSection(id: String, name: String): HomeLayout = updateSection(id) { it.copy(name = name) }

    fun setStyle(id: String, style: SectionStyle): HomeLayout = updateSection(id) { it.copy(style = style) }

    /** Deletes a category (its apps stay installed and visible in "all apps"). */
    fun removeCategory(id: String): HomeLayout =
        if (id == HomeSection.FAVORITES) this else copy(sections = sections.filterNot { it.id == id })

    /**
     * Puts back a category that was removed, where it stood ([at], among all the sections); as it
     * is when one of that id is there.
     */
    fun restoreCategory(category: HomeSection, at: Int): HomeLayout =
        if (category.isFavorites || section(category.id) != null) {
            this
        } else {
            copy(sections = sections.toMutableList().apply { add(at.coerceIn(1, size), category) })
        }

    /** Whether the section has the tile that opens all apps. */
    fun hasAllApps(sectionId: String): Boolean = hasTile(sectionId, HomeItem.AllApps)

    /** Puts the tile that opens all apps at the end of the section, or takes it out. */
    fun withAllApps(sectionId: String, shown: Boolean): HomeLayout = withTile(sectionId, HomeItem.AllApps, shown)

    /** Whether the section has the tile of input [id]. */
    fun hasInput(sectionId: String, id: String): Boolean = hasTile(sectionId, HomeItem.Input(id))

    /** Puts the tile of input [id] at the end of the section, or takes it out. */
    fun withInput(sectionId: String, id: String, shown: Boolean): HomeLayout =
        withTile(sectionId, HomeItem.Input(id), shown)

    /** Whether the section has [tile], one a row may have once and that is no app or folder. */
    private fun hasTile(sectionId: String, tile: HomeItem): Boolean = section(sectionId)?.items?.contains(tile) == true

    private fun withTile(sectionId: String, tile: HomeItem, shown: Boolean): HomeLayout = when {
        section(sectionId) == null || shown == hasTile(sectionId, tile) -> this
        shown -> updateSection(sectionId) { it.copy(items = it.items + tile) }
        else -> updateSection(sectionId) { it.copy(items = it.items - tile) }
    }

    /** Gives input [id] a name of the user's; a blank [label] brings back the firmware's. */
    fun renameInput(id: String, label: String): HomeLayout =
        copy(inputLabels = if (label.isBlank()) inputLabels - id else inputLabels + (id to label.trim()))

    // --- Apps in sections ---

    /** Appends [app] to a section unless it is already there, unhiding it. */
    fun add(sectionId: String, app: AppId): HomeLayout {
        val section = section(sectionId) ?: return this
        if (app in section.apps) return this
        return copy(hidden = hidden - app).updateSection(sectionId) {
            it.copy(items = it.items + HomeItem.AppItem(app))
        }
    }

    /** Takes [app] out of a section, wherever it is in it (a folder left empty goes too). */
    fun remove(sectionId: String, app: AppId): HomeLayout = updateSection(sectionId) { it.without(app) }

    /** Moves an item [by] places within its section, stopping at either end. */
    fun moveItem(sectionId: String, key: String, by: Int): HomeLayout = updateSection(sectionId) { section ->
        section.items.moved(section.items.indexOfFirst { it.key == key }, by)?.let { section.copy(items = it) }
            ?: section
    }

    // --- Folders ---

    /** Puts [app] (already in the section) into a new folder at its place. */
    fun createFolder(sectionId: String, folderId: String, name: String, app: AppId): HomeLayout {
        require(folder(folderId) == null) { "folder $folderId exists" }
        return updateSection(sectionId) { section ->
            val at = section.items.indexOf(HomeItem.AppItem(app))
            if (at < 0) return@updateSection section
            section.copy(
                items = section.items.toMutableList().apply {
                    set(at, HomeItem.Folder(folderId, name, listOf(app)))
                },
            )
        }
    }

    /** Moves [app] into a folder, taking it out of wherever else it was in that section. */
    fun addToFolder(folderId: String, app: AppId): HomeLayout {
        val sectionId = sectionOf(folderId) ?: return this
        if (folder(folderId)?.apps?.contains(app) == true) return this
        return copy(hidden = hidden - app).updateSection(sectionId) { section ->
            section.without(app).mapFolder(folderId) { listOf(it.copy(apps = it.apps + app)) }
        }
    }

    /** Takes [app] out of a folder and places it right after the folder (the folder goes if emptied). */
    fun removeFromFolder(folderId: String, app: AppId): HomeLayout {
        val sectionId = sectionOf(folderId) ?: return this
        return updateSection(sectionId) { section ->
            section.mapFolder(folderId) { folder ->
                if (app !in folder.apps) return@mapFolder listOf(folder)
                val rest = folder.apps - app
                listOfNotNull(folder.copy(apps = rest).takeIf { rest.isNotEmpty() }, HomeItem.AppItem(app))
            }
        }
    }

    fun renameFolder(folderId: String, name: String): HomeLayout = updateFolder(folderId) { it.copy(name = name) }

    /** Replaces a folder with its apps, in place. */
    fun dissolveFolder(folderId: String): HomeLayout {
        val sectionId = sectionOf(folderId) ?: return this
        return updateSection(sectionId) { section -> section.mapFolder(folderId) { it.apps.map(HomeItem::AppItem) } }
    }

    fun folder(id: String): HomeItem.Folder? =
        sections.flatMap { it.items }.filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id }

    // --- Apps ---

    /** Hides [app] from the home screen, taking it out of every section. */
    fun hide(app: AppId): HomeLayout = copy(sections = sections.map { it.without(app) }, hidden = hidden + app)

    fun unhide(app: AppId): HomeLayout = copy(hidden = hidden - app)

    /** Gives [app] a custom name; a blank [label] restores the app's own. */
    fun rename(app: AppId, label: String): HomeLayout =
        copy(labels = if (label.isBlank()) labels - app else labels + (app to label.trim()))

    /** Gives [app] a name to say in voice commands; a blank [name] takes it away. */
    fun voiceName(app: AppId, name: String): HomeLayout =
        copy(voiceNames = if (name.isBlank()) voiceNames - app else voiceNames + (app to name.trim()))

    /**
     * [installed] under the names people may say: the custom one, a renamed app's own as well, and
     * the voice name where there is one.
     */
    fun spokenNames(installed: List<App>): List<App> =
        installed.map { app -> labels[app.id]?.let { app.copy(label = it) } ?: app } +
            installed.filter { it.id in labels } +
            installed.mapNotNull { app -> voiceNames[app.id]?.let { app.copy(label = it) } }

    /**
     * What the home screen shows for [installed] apps (custom names applied): each section
     * with its installed items and the device's [inputs], every visible app (TV apps first, then
     * by name in [locale]'s alphabet, ё with е) and the hidden ones, which "All apps" shows when asked.
     */
    fun arrange(installed: List<App>, locale: Locale, inputs: List<InputSource> = emptyList()): HomeContent {
        val byId = installed.associate { it.id to it.copy(label = labels[it.id] ?: it.label) }
        val names = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        val byName = compareBy<App, String>(names) { it.label }
        return HomeContent(
            sections = sections.map { section ->
                SectionContent(
                    section.id,
                    section.name,
                    section.style,
                    section.items.mapNotNull { it.resolve(byId, inputs) },
                )
            },
            apps = byId.values.filterNot { it.id in hidden }
                .sortedWith(compareByDescending<App> { it.isTvApp }.then(byName)),
            hidden = byId.values.filter { it.id in hidden }.sortedWith(byName),
        )
    }

    private fun HomeItem.resolve(byId: Map<AppId, App>, inputs: List<InputSource>): TileContent? = when (this) {
        is HomeItem.AppItem -> byId[app]?.let { TileContent.AppTile(it, key) }

        is HomeItem.Folder ->
            apps.mapNotNull(byId::get).takeIf { it.isNotEmpty() }?.let { TileContent.FolderTile(id, name, it, key) }

        HomeItem.AllApps -> TileContent.AllAppsTile

        is HomeItem.Input -> inputs.firstOrNull { it.id == id }?.let { TileContent.InputTile(id, nameOf(it), key) }
    }

    /** [input]'s name on screen: the user's, else the firmware's. */
    fun nameOf(input: InputSource): String = inputLabels[input.id] ?: input.name

    private fun sectionOf(folderId: String): String? =
        sections.firstOrNull { section -> section.items.any { it is HomeItem.Folder && it.id == folderId } }?.id

    private fun updateSection(id: String, transform: (HomeSection) -> HomeSection): HomeLayout =
        copy(sections = sections.map { if (it.id == id) transform(it) else it })

    private fun updateFolder(id: String, transform: (HomeItem.Folder) -> HomeItem.Folder): HomeLayout =
        copy(sections = sections.map { section -> section.mapFolder(id) { listOf(transform(it)) } })
}

/** [this] section without [app], dropping a folder it leaves empty. */
private fun HomeSection.without(app: AppId): HomeSection = copy(
    items = items.mapNotNull { item ->
        when (item) {
            is HomeItem.AppItem -> item.takeIf { it.app != app }
            is HomeItem.Folder -> item.copy(apps = item.apps - app).takeIf { it.apps.isNotEmpty() }
            HomeItem.AllApps, is HomeItem.Input -> item
        }
    },
)

/** [this] section with folder [id] replaced by what [transform] makes of it. */
private fun HomeSection.mapFolder(id: String, transform: (HomeItem.Folder) -> List<HomeItem>): HomeSection =
    copy(items = items.flatMap { if (it is HomeItem.Folder && it.id == id) transform(it) else listOf(it) })

/** The list with the element at [from] moved [by] places, clamped; null if nothing moves. */
internal fun <T> List<T>.moved(from: Int, by: Int): List<T>? {
    if (from < 0) return null
    val to = (from + by).coerceIn(0, lastIndex)
    if (to == from) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}

data class HomeContent(val sections: List<SectionContent>, val apps: List<App>, val hidden: List<App>)

data class SectionContent(val id: String, val name: String, val style: SectionStyle, val tiles: List<TileContent>) {
    val isFavorites: Boolean get() = id == HomeSection.FAVORITES
}

sealed interface TileContent {
    val key: String

    data class AppTile(val app: App, override val key: String) : TileContent

    data class FolderTile(val id: String, val name: String, val apps: List<App>, override val key: String) : TileContent

    /** The shortcut to the "All apps" section. */
    data object AllAppsTile : TileContent {
        override val key: String = HomeItem.AllApps.key
    }

    /** A video input, under [name] (the user's or the firmware's). */
    data class InputTile(val id: String, val name: String, override val key: String) : TileContent
}

/** Persisted [HomeLayout]. */
interface HomeLayoutRepository {
    val layout: Flow<HomeLayout>

    /** Applies [transform] atomically to the stored layout. */
    suspend fun update(transform: (HomeLayout) -> HomeLayout)
}
