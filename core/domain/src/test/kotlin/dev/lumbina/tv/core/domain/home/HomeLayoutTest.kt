package dev.lumbina.tv.core.domain.home

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.InputSource
import dev.lumbina.tv.core.domain.home.HomeItem.AppItem
import dev.lumbina.tv.core.domain.home.HomeItem.Folder
import dev.lumbina.tv.core.domain.home.HomeSection.Companion.FAVORITES
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HomeLayoutTest {

    private fun id(name: String) = AppId("pkg.$name", "pkg.$name.Main")
    private val a = id("a")
    private val b = id("b")
    private val c = id("c")

    private fun HomeLayout.items(section: String = FAVORITES) = section(section)!!.items

    @Test
    fun `favorites keep insertion order without duplicates`() {
        val layout = HomeLayout().add(FAVORITES, a).add(FAVORITES, b).add(FAVORITES, a)
        assertEquals(listOf(a, b), layout.favorites.apps)
    }

    @Test
    fun `items move within their section and stop at both ends`() {
        val layout = HomeLayout().add(FAVORITES, a).add(FAVORITES, b).add(FAVORITES, c)
        assertEquals(listOf(b, a, c), layout.moveItem(FAVORITES, AppItem(a).key, 1).favorites.apps)
        assertEquals(listOf(c, a, b), layout.moveItem(FAVORITES, AppItem(c).key, -5).favorites.apps)
        assertEquals(layout, layout.moveItem(FAVORITES, AppItem(c).key, 1))
        assertEquals(layout, layout.moveItem(FAVORITES, "unknown", 1))
    }

    @Test
    fun `categories are added, renamed, styled and removed behind favorites`() {
        val layout = HomeLayout().addCategory("films", "Films").addCategory("kids", "Kids")
            .renameSection("kids", "Children").setStyle("films", SectionStyle.Grid)
        assertEquals(listOf(FAVORITES, "films", "kids"), layout.sections.map { it.id })
        assertEquals("Children", layout.section("kids")!!.name)
        assertEquals(SectionStyle.Grid, layout.section("films")!!.style)
        assertEquals(listOf(FAVORITES, "kids"), layout.removeCategory("films").sections.map { it.id })
        assertEquals(layout, layout.removeCategory(FAVORITES))
        val films = layout.section("films")!!
        val at = layout.sections.indexOf(films)
        assertEquals(layout, layout.removeCategory("films").restoreCategory(films, at))
        assertEquals(layout, layout.restoreCategory(films, at))
    }

    @Test
    fun `a category is put back behind favorites and within the sections there are`() {
        val layout = HomeLayout().addCategory("films", "Films")
        val kids = HomeSection("kids", "Kids")
        assertEquals(listOf(FAVORITES, "kids", "films"), layout.restoreCategory(kids, 0).sections.map { it.id })
        assertEquals(listOf(FAVORITES, "films", "kids"), layout.restoreCategory(kids, 9).sections.map { it.id })
        assertEquals(layout, layout.restoreCategory(layout.favorites, 1))
    }

    @Test
    fun `an app can be in several sections but only once in each`() {
        val layout = HomeLayout().addCategory("films", "Films").add(FAVORITES, a).add("films", a).add("films", a)
        assertEquals(listOf(a), layout.favorites.apps)
        assertEquals(listOf(a), layout.section("films")!!.apps)
    }

    @Test
    fun `a folder takes an app's place and gathers others from the same section`() {
        val layout = HomeLayout().add(FAVORITES, a).add(FAVORITES, b).add(FAVORITES, c)
            .createFolder(FAVORITES, "f1", "Video", b)
            .addToFolder("f1", c)
            .addToFolder("f1", a)
        assertEquals(listOf(Folder("f1", "Video", listOf(b, c, a))), layout.items())
        assertEquals("Films", layout.renameFolder("f1", "Films").folder("f1")!!.name)
    }

    @Test
    fun `taking the last app out of a folder removes the folder`() {
        val layout = HomeLayout().add(FAVORITES, a).add(FAVORITES, b).createFolder(FAVORITES, "f1", "F", a)
            .addToFolder("f1", b)
        val one = layout.removeFromFolder("f1", a)
        assertEquals(listOf(Folder("f1", "F", listOf(b)), AppItem(a)), one.items())
        val none = one.removeFromFolder("f1", b)
        assertEquals(listOf(AppItem(b), AppItem(a)), none.items())
        assertNull(none.folder("f1"))
    }

    @Test
    fun `dissolving a folder puts its apps back in place`() {
        val layout = HomeLayout().add(FAVORITES, a).add(FAVORITES, b).add(FAVORITES, c)
            .createFolder(FAVORITES, "f1", "F", b).addToFolder("f1", c)
        assertEquals(listOf(AppItem(a), AppItem(b), AppItem(c)), layout.dissolveFolder("f1").items())
    }

    @Test
    fun `hiding takes an app out of every section and folder, adding unhides`() {
        val layout = HomeLayout().addCategory("films", "Films").add(FAVORITES, a).add("films", a).add("films", b)
            .createFolder("films", "f1", "F", b).addToFolder("f1", a)
        val hidden = layout.hide(a)
        assertFalse(a in hidden.favorites.apps)
        assertEquals(listOf(Folder("f1", "F", listOf(b))), hidden.items("films"))
        assertTrue(a in hidden.hidden)
        assertFalse(a in hidden.add(FAVORITES, a).hidden)
    }

    @Test
    fun `invalid layouts are rejected`() {
        assertThrows<IllegalArgumentException> { HomeLayout(sections = emptyList()) }
        assertThrows<IllegalArgumentException> {
            HomeLayout(sections = listOf(HomeSection(FAVORITES, "", items = listOf(AppItem(a)))), hidden = setOf(a))
        }
        assertThrows<IllegalArgumentException> {
            HomeLayout(sections = listOf(HomeSection(FAVORITES, "", items = listOf(Folder("f", "F", emptyList())))))
        }
    }

    @Test
    fun `arranging applies custom names, skips missing apps and sorts TV apps first`() {
        val installed = listOf(
            App(c, "zebra", isTvApp = false),
            App(b, "Beta", isTvApp = true),
            App(a, "alpha", isTvApp = true),
            App(id("d"), "Delta", isTvApp = false),
        )
        val layout = HomeLayout().add(FAVORITES, id("gone")).add(FAVORITES, c).add(FAVORITES, b)
            .createFolder(FAVORITES, "f1", "F", b).addToFolder("f1", id("gone2"))
            .hide(id("d")).rename(b, "  My Beta ")
        val content = layout.arrange(installed, Locale.ENGLISH)
        val tiles = content.sections.single().tiles
        assertEquals("zebra", (tiles[0] as TileContent.AppTile).app.label)
        assertEquals(listOf("My Beta"), (tiles[1] as TileContent.FolderTile).apps.map { it.label })
        assertEquals(listOf("alpha", "My Beta", "zebra"), content.apps.map { it.label })
        assertEquals(listOf("Delta"), content.hidden.map { it.label })
        assertEquals("Beta", layout.rename(b, " ").arrange(installed, Locale.ENGLISH).apps[1].label)
    }

    @Test
    fun `names sort in the language's alphabet, ё among е`() {
        val installed = listOf("Яндекс", "Ёлки", "Арбуз", "Ещё").mapIndexed { i, name ->
            App(id("p$i"), name, isTvApp = true)
        }
        val apps = HomeLayout().arrange(installed, Locale.forLanguageTag("ru")).apps
        assertEquals(listOf("Арбуз", "Ёлки", "Ещё", "Яндекс"), apps.map { it.label })
    }

    @Test
    fun `adding an app to the folder it is already in changes nothing`() {
        val layout = HomeLayout().add(FAVORITES, a).createFolder(FAVORITES, "f1", "F", a)
        assertEquals(layout, layout.addToFolder("f1", a))
    }

    @Test
    fun `the all-apps tile moves, goes and comes back like a tile`() {
        val a = AppId("pkg.a", "A")
        val layout = HomeLayout().add(HomeSection.FAVORITES, a).withAllApps(HomeSection.FAVORITES, true)
        assertEquals(listOf(HomeItem.AppItem(a), HomeItem.AllApps), layout.favorites.items)
        val moved = layout.moveItem(HomeSection.FAVORITES, HomeItem.AllApps.key, -1)
        assertEquals(listOf(HomeItem.AllApps, HomeItem.AppItem(a)), moved.favorites.items)
        // Hiding an app, or taking it out, leaves the shortcut where it is.
        assertEquals(listOf(HomeItem.AllApps), moved.hide(a).favorites.items)
        assertEquals(layout, layout.withAllApps(HomeSection.FAVORITES, true))
        val out = layout.withAllApps(HomeSection.FAVORITES, false)
        assertEquals(listOf<HomeItem>(HomeItem.AppItem(a)), out.favorites.items)
        assertEquals(listOf(a), layout.favorites.apps)
    }

    @Test
    fun `any row may have the all-apps tile, once`() {
        val layout = HomeLayout().addCategory("games", "Games")
            .withAllApps(HomeSection.FAVORITES, true).withAllApps("games", true)
        assertTrue(layout.hasAllApps(HomeSection.FAVORITES))
        assertEquals(listOf<HomeItem>(HomeItem.AllApps), layout.section("games")!!.items)
        assertEquals(layout, layout.withAllApps("games", true))
        assertEquals(layout, layout.withAllApps("missing", true))
        assertFalse(layout.withAllApps("games", false).hasAllApps("games"))
        assertTrue(layout.withAllApps("games", false).hasAllApps(HomeSection.FAVORITES))
        assertThrows<IllegalArgumentException> {
            layout.copy(sections = layout.sections.map { it.copy(items = it.items + HomeItem.AllApps) })
        }
    }

    @Test
    fun `each input's tile stands once per row, and shows only where the device has it`() {
        val layout = HomeLayout().add(
            FAVORITES,
            a,
        ).withInput(FAVORITES, "1281", true).withInput(FAVORITES, "1282", true)
        assertEquals(listOf(AppItem(a), HomeItem.Input("1281"), HomeItem.Input("1282")), layout.items())
        assertEquals(layout, layout.withInput(FAVORITES, "1281", true))
        assertEquals(listOf(a), layout.favorites.apps)
        assertEquals(listOf(AppItem(a), HomeItem.Input("1281"), HomeItem.Input("1282")), layout.hide(b).items())
        assertFalse(layout.withInput(FAVORITES, "1281", false).hasInput(FAVORITES, "1281"))
        assertThrows<IllegalArgumentException> {
            layout.copy(sections = layout.sections.map { it.copy(items = it.items + HomeItem.Input("1281")) })
        }
        val tiles = layout.arrange(
            listOf(App(a, "A", isTvApp = true)),
            Locale.ENGLISH,
            listOf(InputSource("1281", "HDMI 1")),
        )
            .sections.single().tiles
        assertEquals(listOf("app:${a.packageName}/${a.activityName}", "input:1281"), tiles.map { it.key })
    }

    @Test
    fun `an input takes the user's name, and a blank one brings back the device's`() {
        val hdmi = InputSource("1281", "HDMI 1")
        val named = HomeLayout().withInput(FAVORITES, hdmi.id, true).renameInput(hdmi.id, " Приставка ")
        val tile = named.arrange(emptyList(), Locale.ENGLISH, listOf(hdmi)).sections.single().tiles.single()
        assertEquals(TileContent.InputTile("1281", "Приставка", "input:1281"), tile)
        assertEquals("HDMI 1", named.renameInput(hdmi.id, "").nameOf(hdmi))
    }
}
