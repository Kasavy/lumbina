package dev.lumbina.tv.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: the favorites list becomes the first section of a general layout with
 * categories, folders and custom app names. Favorites keep their order.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`style` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `section_items` (`sectionId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`appId` TEXT, `folderId` TEXT, PRIMARY KEY(`sectionId`, `position`))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `folder_apps` (`folderId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`appId` TEXT NOT NULL, PRIMARY KEY(`folderId`, `position`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_labels` (`appId` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                "PRIMARY KEY(`appId`))",
        )
        db.execSQL("INSERT INTO `sections` (`id`, `name`, `style`, `position`) VALUES ('favorites', '', 'Row', 0)")
        // Renumber from 0 in the old order (positions may have gaps).
        db.execSQL(
            "INSERT INTO `section_items` (`sectionId`, `position`, `appId`, `folderId`) " +
                "SELECT 'favorites', (SELECT COUNT(*) FROM `favorites` f2 WHERE f2.`position` < f.`position`), " +
                "f.`appId`, NULL FROM `favorites` f",
        )
        db.execSQL("DROP TABLE `favorites`")
    }
}

/** v2 → v3: names to call apps by in voice commands. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_voice_names` (`appId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "PRIMARY KEY(`appId`))",
        )
    }
}
