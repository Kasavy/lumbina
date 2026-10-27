package dev.lumbina.tv.core.data.home

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.SectionStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "sections")
internal data class SectionEntity(@PrimaryKey val id: String, val name: String, val style: String, val position: Int)

/** One slot of a section: an app or a folder; the shortcut to all apps and an input stand in [appId] as marks. */
@Entity(tableName = "section_items", primaryKeys = ["sectionId", "position"])
internal data class SectionItemEntity(
    val sectionId: String,
    val position: Int,
    val appId: String?,
    val folderId: String?,
)

@Entity(tableName = "folders")
internal data class FolderEntity(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "folder_apps", primaryKeys = ["folderId", "position"])
internal data class FolderAppEntity(val folderId: String, val position: Int, val appId: String)

@Entity(tableName = "hidden_apps")
internal data class HiddenAppEntity(@PrimaryKey val appId: String)

@Entity(tableName = "app_labels")
internal data class AppLabelEntity(@PrimaryKey val appId: String, val label: String)

@Entity(tableName = "app_voice_names")
internal data class AppVoiceNameEntity(@PrimaryKey val appId: String, val name: String)

/** Everything the layout is made of, read in one go. */
internal data class LayoutRows(
    val sections: List<SectionEntity>,
    val items: List<SectionItemEntity>,
    val folders: List<FolderEntity>,
    val folderApps: List<FolderAppEntity>,
    val hidden: List<HiddenAppEntity>,
    val labels: List<AppLabelEntity>,
    val voiceNames: List<AppVoiceNameEntity>,
)

// One query and one insert per table, plus the read-modify-write they serve.
@Suppress("TooManyFunctions")
@Dao
internal abstract class HomeLayoutDao {
    /**
     * Emits whenever any of the layout tables changes. The value itself means nothing: callers rely
     * on Room re-emitting on every write, so it must not be passed through `distinctUntilChanged`.
     */
    @Query(
        "SELECT (SELECT COUNT(*) FROM sections) + (SELECT COUNT(*) FROM section_items) + " +
            "(SELECT COUNT(*) FROM folders) + (SELECT COUNT(*) FROM folder_apps) + " +
            "(SELECT COUNT(*) FROM hidden_apps) + (SELECT COUNT(*) FROM app_labels) + " +
            "(SELECT COUNT(*) FROM app_voice_names)",
    )
    protected abstract fun changes(): Flow<Int>

    @Query("SELECT * FROM sections ORDER BY position")
    protected abstract suspend fun sections(): List<SectionEntity>

    @Query("SELECT * FROM section_items ORDER BY sectionId, position")
    protected abstract suspend fun items(): List<SectionItemEntity>

    @Query("SELECT * FROM folders")
    protected abstract suspend fun folders(): List<FolderEntity>

    @Query("SELECT * FROM folder_apps ORDER BY folderId, position")
    protected abstract suspend fun folderApps(): List<FolderAppEntity>

    @Query("SELECT * FROM hidden_apps")
    protected abstract suspend fun hidden(): List<HiddenAppEntity>

    @Query("SELECT * FROM app_labels")
    protected abstract suspend fun labels(): List<AppLabelEntity>

    @Query("SELECT * FROM app_voice_names")
    protected abstract suspend fun voiceNames(): List<AppVoiceNameEntity>

    @Query("DELETE FROM sections")
    protected abstract suspend fun clearSections()

    @Query("DELETE FROM section_items")
    protected abstract suspend fun clearItems()

    @Query("DELETE FROM folders")
    protected abstract suspend fun clearFolders()

    @Query("DELETE FROM folder_apps")
    protected abstract suspend fun clearFolderApps()

    @Query("DELETE FROM hidden_apps")
    protected abstract suspend fun clearHidden()

    @Query("DELETE FROM app_labels")
    protected abstract suspend fun clearLabels()

    @Query("DELETE FROM app_voice_names")
    protected abstract suspend fun clearVoiceNames()

    @Insert protected abstract suspend fun insertSections(rows: List<SectionEntity>)

    @Insert protected abstract suspend fun insertItems(rows: List<SectionItemEntity>)

    @Insert protected abstract suspend fun insertFolders(rows: List<FolderEntity>)

    @Insert protected abstract suspend fun insertFolderApps(rows: List<FolderAppEntity>)

    @Insert protected abstract suspend fun insertHidden(rows: List<HiddenAppEntity>)

    @Insert protected abstract suspend fun insertLabels(rows: List<AppLabelEntity>)

    @Insert protected abstract suspend fun insertVoiceNames(rows: List<AppVoiceNameEntity>)

    fun layout(): Flow<HomeLayout> = changes().map { read().toLayout() }

    @Transaction
    open suspend fun read(): LayoutRows =
        LayoutRows(sections(), items(), folders(), folderApps(), hidden(), labels(), voiceNames())

    /** Read-modify-write in one transaction, so concurrent edits don't lose each other. */
    @Transaction
    open suspend fun update(transform: (HomeLayout) -> HomeLayout) {
        val current = read().toLayout()
        val next = transform(current)
        if (next != current) write(next.toRows())
    }

    private suspend fun write(rows: LayoutRows) {
        clearSections()
        clearItems()
        clearFolders()
        clearFolderApps()
        clearHidden()
        clearLabels()
        clearVoiceNames()
        insertSections(rows.sections)
        insertItems(rows.items)
        insertFolders(rows.folders)
        insertFolderApps(rows.folderApps)
        insertHidden(rows.hidden)
        insertLabels(rows.labels)
        insertVoiceNames(rows.voiceNames)
    }
}

internal class RoomHomeLayoutRepository(private val dao: HomeLayoutDao) : HomeLayoutRepository {
    override val layout: Flow<HomeLayout> = dao.layout()

    override suspend fun update(transform: (HomeLayout) -> HomeLayout) = dao.update(transform)
}

internal fun HomeLayout.toRows(): LayoutRows {
    val folders = sections.flatMap { it.items }.filterIsInstance<HomeItem.Folder>()
    return LayoutRows(
        sections = sections.mapIndexed { i, s -> SectionEntity(s.id, s.name, s.style.name, i) },
        items = sections.flatMap { section ->
            section.items.mapIndexed { i, item ->
                when (item) {
                    is HomeItem.AppItem -> SectionItemEntity(section.id, i, item.app.encode(), null)
                    is HomeItem.Folder -> SectionItemEntity(section.id, i, null, item.id)
                    HomeItem.AllApps -> SectionItemEntity(section.id, i, ALL_APPS_SHORTCUT, null)
                    is HomeItem.Input -> SectionItemEntity(section.id, i, INPUT + item.id, null)
                }
            }
        },
        folders = folders.map { FolderEntity(it.id, it.name) },
        folderApps = folders.flatMap { folder ->
            folder.apps.mapIndexed { i, app -> FolderAppEntity(folder.id, i, app.encode()) }
        },
        hidden = hidden.map { HiddenAppEntity(it.encode()) },
        labels = labels.map { (app, label) -> AppLabelEntity(app.encode(), label) } +
            inputLabels.map { (id, label) -> AppLabelEntity(INPUT + id, label) },
        voiceNames = voiceNames.map { (app, name) -> AppVoiceNameEntity(app.encode(), name) },
    )
}

/**
 * Rebuilds the layout, dropping whatever would break its invariants (duplicates, hidden apps
 * in sections, empty folders) instead of failing on a damaged database.
 */
internal fun LayoutRows.toLayout(): HomeLayout {
    val hiddenApps = hidden.mapNotNull { decode(it.appId) }.toSet()
    val folderApps = folderApps.groupBy { it.folderId }.mapValues { (_, rows) -> rows.mapNotNull { decode(it.appId) } }
    val folderNames = folders.associate { it.id to it.name }
    val usedFolders = mutableSetOf<String>()
    val itemsBySection = items.groupBy { it.sectionId }
    val stored = sections.map { entity ->
        val seen = mutableSetOf<AppId>()
        val marks = mutableSetOf<String>()
        fun keep(app: AppId) = app !in hiddenApps && seen.add(app)
        val sectionItems = itemsBySection[entity.id].orEmpty().mapNotNull { row ->
            val mark = row.appId?.let(::markOf)
            val app = row.appId?.let(::decode)
            val folderId = row.folderId
            when {
                // Once per section: a second one is a damaged row.
                mark != null -> mark.takeIf { marks.add(mark.key) }

                app != null -> HomeItem.AppItem(app).takeIf { keep(app) }

                folderId != null && usedFolders.add(folderId) -> folderApps[folderId].orEmpty().filter(::keep)
                    .takeIf { it.isNotEmpty() }
                    ?.let { HomeItem.Folder(folderId, folderNames[folderId].orEmpty(), it) }

                else -> null
            }
        }
        val style = SectionStyle.entries.firstOrNull { it.name == entity.style } ?: SectionStyle.Row
        HomeSection(entity.id, entity.name, style, sectionItems)
    }
    val favorites = stored.firstOrNull { it.isFavorites } ?: HomeSection.emptyFavorites()
    return HomeLayout(
        sections = listOf(favorites) + stored.filterNot { it.isFavorites },
        hidden = hiddenApps,
        labels = labels.mapNotNull { row -> decode(row.appId)?.let { it to row.label } }.toMap(),
        voiceNames = voiceNames.mapNotNull { row -> decode(row.appId)?.let { it to row.name } }.toMap(),
        inputLabels = labels.filter {
            it.appId.startsWith(INPUT)
        }.associate { it.appId.removePrefix(INPUT) to it.label },
    )
}

/** The shortcut to all apps or an input's tile for what stands in an item's app column; null for an app. */
private fun markOf(value: String): HomeItem? = when {
    value == ALL_APPS_SHORTCUT -> HomeItem.AllApps
    value.startsWith(INPUT) && value.length > INPUT.length -> HomeItem.Input(value.removePrefix(INPUT))
    else -> null
}

/** `package/fully.qualified.Activity`; unlike [AppId.flatten], always reversible. */
internal fun AppId.encode(): String = "$packageName/$activityName"

/** Null also for a mark ([MARK]): an input's id may hold a '/' too, and is no app. */
internal fun decode(value: String): AppId? {
    if (value.startsWith(MARK)) return null
    val packageName = value.substringBefore('/', missingDelimiterValue = "")
    val activityName = value.substringAfter('/', missingDelimiterValue = "")
    return if (packageName.isBlank() || activityName.isBlank()) null else AppId(packageName, activityName)
}

/** How what stands in an app's column but is no app begins: no package name does. */
private const val MARK = "@"

/** What stands in an item's app column for the tile that opens all apps. */
internal const val ALL_APPS_SHORTCUT = "${MARK}all-apps"

/** What stands before an input's id in an item's app column, and in the names' table for its name. */
internal const val INPUT = "${MARK}input:"
