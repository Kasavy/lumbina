package dev.lumbina.tv.feature.home

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperKind
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeSection
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickGroupsTest {

    private fun app(name: String, tv: Boolean = true) = App(AppId("com.$name", ".Main"), name, isTvApp = tv)

    private val ivi = app("ivi")
    private val kion = app("kion")
    private val rutube = app("rutube")
    private val smart = app("smarttube")
    private val wink = app("wink")

    /** Not a TV app: home puts it after them all. */
    private val aimp = app("aimp", tv = false)

    /** In the order home sorts them. */
    private val all = listOf(ivi, kion, rutube, smart, wink, aimp)

    private val films = HomeSection(
        id = "films",
        name = "Films",
        items = listOf(
            HomeItem.AppItem(wink.id),
            HomeItem.Folder("f1", "Kino", listOf(smart.id)),
            HomeItem.AppItem(rutube.id),
        ),
    )

    private fun groups(section: HomeSection, apps: List<App> = all) = pickGroups(section, apps, Locale.ROOT)

    @Test
    fun `the row's apps come first in its order, folders' apps where the folder stands`() {
        assertEquals(listOf(wink, smart, rutube), groups(films).inRow)
    }

    @Test
    fun `the rest go by name alone, TV apps or not, and leave out what is in the row`() {
        assertEquals(listOf(aimp, ivi, kion), groups(films).rest)
    }

    @Test
    fun `an app of the row that is not installed is left out`() {
        val gone = films.copy(items = films.items + HomeItem.AppItem(AppId("com.gone", ".Main")))
        assertEquals(listOf(wink, smart, rutube), groups(gone).inRow)
    }

    @Test
    fun `the focus starts on the row's first app, or on the first of the rest when the row is empty`() {
        assertEquals(wink, groups(films).first)
        assertEquals(aimp, groups(HomeSection.emptyFavorites()).first)
        assertNull(groups(films, emptyList()).first)
    }

    @Test
    fun `the window starts on the all-apps tile in the row, the row's apps, else the inputs, else that tile`() {
        assertEquals(PickStart.Shortcut, pickStart(shortcutInRow = true, rowEmpty = true, hasInputs = true))
        assertEquals(PickStart.Apps, pickStart(shortcutInRow = false, rowEmpty = false, hasInputs = true))
        assertEquals(PickStart.Inputs, pickStart(shortcutInRow = false, rowEmpty = true, hasInputs = true))
        assertEquals(PickStart.Shortcut, pickStart(shortcutInRow = false, rowEmpty = true, hasInputs = false))
    }

    @Test
    fun `an app inside a folder of the row is said to be in it`() {
        assertEquals("Kino", folderOf(films, smart.id))
        assertNull(folderOf(films, wink.id))
        assertNull(folderOf(films, ivi.id))
    }

    @Test
    fun `the count is of the ticked tiles, an app not installed or hidden is not counted`() {
        assertEquals(3, chosenCount(films, all))
        val gone = films.copy(items = films.items + HomeItem.AppItem(AppId("com.gone", ".Main")))
        assertEquals(3, chosenCount(gone, all))
        assertEquals(2, chosenCount(films, all - wink))
    }

    private fun picture(name: String) = WallpaperItem("file:///photos/$name", WallpaperKind.Image, title = name)

    @Test
    fun `the banner panel starts on the picture the tile shows, else on the app's own`() {
        val pictures = listOf(picture("a.jpg"), picture("b.jpg"))
        assertEquals(0, bannerStart(null, pictures))
        assertEquals(2, bannerStart("file:///photos/b.jpg", pictures))
        assertEquals(0, bannerStart("file:///photos/deleted.jpg", pictures))
    }
}
