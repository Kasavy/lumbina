package dev.lumbina.tv.core.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import dev.lumbina.tv.core.data.home.RoomHomeLayoutRepository
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.SectionStyle
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Builds a real old database from its exported schema, fills it, and opens it as the current version. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val name = "migration-test.db"

    @Test
    fun `favorites survive 1 to 2 in order, hidden apps too`() = runTest {
        create(version = 1) { db ->
            db.execSQL("INSERT INTO favorites VALUES ('pkg.b/pkg.b.Main', 7)")
            db.execSQL("INSERT INTO favorites VALUES ('pkg.a/pkg.a.Main', 3)")
            db.execSQL("INSERT INTO hidden_apps VALUES ('pkg.c/pkg.c.Main')")
        }
        val database = Room.databaseBuilder(context, LumbinaDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        val layout = RoomHomeLayoutRepository(database.homeLayout()).layout.first()
        assertEquals(listOf(HomeSection.FAVORITES), layout.sections.map { it.id })
        assertEquals(listOf(AppId("pkg.a", "pkg.a.Main"), AppId("pkg.b", "pkg.b.Main")), layout.favorites.apps)
        assertEquals(setOf(AppId("pkg.c", "pkg.c.Main")), layout.hidden)
        database.close()
    }

    @Test
    fun `the layout survives 2 to 3, and voice names can be kept after it`() = runTest {
        create(version = 2) { db ->
            db.execSQL("INSERT INTO sections VALUES ('favorites', '', 'Row', 0)")
            db.execSQL("INSERT INTO section_items VALUES ('favorites', 0, 'pkg.a/pkg.a.Main', NULL)")
            db.execSQL("INSERT INTO app_labels VALUES ('pkg.a/pkg.a.Main', 'Films')")
        }
        val database = Room.databaseBuilder(context, LumbinaDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        val repository = RoomHomeLayoutRepository(database.homeLayout())
        val app = AppId("pkg.a", "pkg.a.Main")
        assertEquals(listOf(app), repository.layout.first().favorites.apps)
        assertEquals(mapOf(app to "Films"), repository.layout.first().labels)
        repository.update { it.voiceName(app, "кино") }
        assertEquals(mapOf(app to "кино"), repository.layout.first().voiceNames)
        database.close()
    }

    @Test
    fun `favorites' style survives 3 to 5, a row and a grid`() = runTest {
        fun styles(favorites: String): Map<String, SectionStyle> {
            create(version = 3) { db ->
                db.execSQL("INSERT INTO sections VALUES ('favorites', '', '$favorites', 0)")
                db.execSQL("INSERT INTO sections VALUES ('films', 'Films', 'Row', 1)")
            }
            val database = Room.databaseBuilder(context, LumbinaDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .allowMainThreadQueries()
                .build()
            val repository = RoomHomeLayoutRepository(database.homeLayout())
            val layout = runBlocking { repository.layout.first() }
            database.close()
            return layout.sections.associate { it.id to it.style }
        }
        assertEquals(mapOf("favorites" to SectionStyle.Row, "films" to SectionStyle.Row), styles("Row"))
        assertEquals(mapOf("favorites" to SectionStyle.Grid, "films" to SectionStyle.Row), styles("Grid"))
    }

    @Test
    fun `the all-apps shortcut ends favorites on 4 to 5`() = runTest {
        create(version = 4) { db ->
            db.execSQL("INSERT INTO sections VALUES ('favorites', '', 'Dock', 0)")
            db.execSQL("INSERT INTO section_items VALUES ('favorites', 0, 'pkg.a/pkg.a.Main', NULL)")
        }
        val database = Room.databaseBuilder(context, LumbinaDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        val favorites = RoomHomeLayoutRepository(database.homeLayout()).layout.first().favorites
        assertEquals(listOf(HomeItem.AppItem(AppId("pkg.a", "pkg.a.Main")), HomeItem.AllApps), favorites.items)
        database.close()
    }

    @Test
    fun `a 0_0_3 home comes through 3 to 5 whole, the shortcut after its apps`() = runTest {
        create(version = 3) { db ->
            db.execSQL("INSERT INTO sections VALUES ('favorites', '', 'Grid', 0)")
            db.execSQL("INSERT INTO sections VALUES ('films', 'Films', 'Row', 1)")
            db.execSQL("INSERT INTO section_items VALUES ('favorites', 0, 'pkg.a/pkg.a.Main', NULL)")
            db.execSQL("INSERT INTO section_items VALUES ('favorites', 1, 'pkg.b/pkg.b.Main', NULL)")
            db.execSQL("INSERT INTO section_items VALUES ('films', 0, 'pkg.c/pkg.c.Main', NULL)")
        }
        val database = Room.databaseBuilder(context, LumbinaDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        val layout = RoomHomeLayoutRepository(database.homeLayout()).layout.first()
        assertEquals(listOf(HomeSection.FAVORITES, "films"), layout.sections.map { it.id })
        assertEquals(
            listOf(
                HomeItem.AppItem(AppId("pkg.a", "pkg.a.Main")),
                HomeItem.AppItem(AppId("pkg.b", "pkg.b.Main")),
                HomeItem.AllApps,
            ),
            layout.favorites.items,
        )
        assertEquals(SectionStyle.Grid, layout.favorites.style)
        assertEquals(listOf(AppId("pkg.c", "pkg.c.Main")), layout.sections.single { it.id == "films" }.apps)
        database.close()
    }

    private fun create(version: Int, fill: (SupportSQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/dev.lumbina.tv.core.data.LumbinaDatabase/$version.json").readText())
            .getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
        helper.writableDatabase.use(fill)
        helper.close()
    }
}
