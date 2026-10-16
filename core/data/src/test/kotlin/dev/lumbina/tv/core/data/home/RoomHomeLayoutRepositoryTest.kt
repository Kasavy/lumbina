package dev.lumbina.tv.core.data.home

import androidx.room.Room
import dev.lumbina.tv.core.data.LumbinaDatabase
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeSection.Companion.FAVORITES
import dev.lumbina.tv.core.domain.home.SectionStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomHomeLayoutRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        LumbinaDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val repository = RoomHomeLayoutRepository(database.homeLayout())

    private val a = AppId("pkg.a", "pkg.a.Main")
    private val b = AppId("pkg.b", "other.pkg.Launcher")
    private val c = AppId("pkg.c", "pkg.c.Main")

    @After
    fun close() = database.close()

    @Test
    fun `an empty database is a new layout, with the all-apps shortcut in favorites`() = runTest {
        assertEquals(HomeLayout().withAllAppsShortcut(true), repository.layout.first())
    }

    @Test
    fun `sections, folders, hidden apps and names round-trip`() = runTest {
        val expected = HomeLayout()
            .add(FAVORITES, a).add(FAVORITES, b)
            .addCategory("films", "Films").setStyle("films", SectionStyle.Grid)
            .add("films", b).add("films", a).createFolder("films", "f1", "Folder", a)
            .hide(c).rename(b, "Bee").voiceName(a, "эй")
        repository.update { expected }
        assertEquals(expected, repository.layout.first())
    }

    @Test
    fun `damaged rows are dropped instead of breaking the layout`() {
        val rows = LayoutRows(
            sections = listOf(SectionEntity("films", "Films", "Unknown", 0)),
            items = listOf(
                SectionItemEntity("films", 0, a.encode(), null),
                SectionItemEntity("films", 1, a.encode(), null),
                SectionItemEntity("films", 2, c.encode(), null),
                SectionItemEntity("films", 3, null, "empty"),
            ),
            folders = listOf(FolderEntity("empty", "Empty")),
            folderApps = emptyList(),
            hidden = listOf(HiddenAppEntity(c.encode())),
            labels = emptyList(),
            voiceNames = listOf(AppVoiceNameEntity("broken", "x")),
        )
        val layout = rows.toLayout()
        assertEquals(listOf(FAVORITES, "films"), layout.sections.map { it.id })
        assertEquals(listOf(a), layout.section("films")!!.apps)
        assertEquals(SectionStyle.Row, layout.section("films")!!.style)
    }

    @Test
    fun `app ids round-trip through their encoded form`() {
        assertEquals(b, decode(b.encode()))
        assertEquals(null, decode("garbage"))
    }
}
