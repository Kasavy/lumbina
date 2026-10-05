package dev.lumbina.tv.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.lumbina.tv.core.data.home.AppLabelEntity
import dev.lumbina.tv.core.data.home.AppVoiceNameEntity
import dev.lumbina.tv.core.data.home.FolderAppEntity
import dev.lumbina.tv.core.data.home.FolderEntity
import dev.lumbina.tv.core.data.home.HiddenAppEntity
import dev.lumbina.tv.core.data.home.HomeLayoutDao
import dev.lumbina.tv.core.data.home.SectionEntity
import dev.lumbina.tv.core.data.home.SectionItemEntity

/** The launcher UI's database (main process only). Schemas are exported to `core/data/schemas`. */
@Database(
    entities = [
        SectionEntity::class,
        SectionItemEntity::class,
        FolderEntity::class,
        FolderAppEntity::class,
        HiddenAppEntity::class,
        AppLabelEntity::class,
        AppVoiceNameEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class LumbinaDatabase : RoomDatabase() {
    abstract fun homeLayout(): HomeLayoutDao
}
