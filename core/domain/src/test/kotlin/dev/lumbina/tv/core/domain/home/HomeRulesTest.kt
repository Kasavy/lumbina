package dev.lumbina.tv.core.domain.home

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.home.HomeItem.AppItem
import dev.lumbina.tv.core.domain.home.HomeItem.Folder
import dev.lumbina.tv.core.domain.home.HomeSection.Companion.FAVORITES
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeRulesTest {

    private fun id(name: String) = AppId("pkg.$name", "pkg.$name.Main")
    private val a = id("a")
    private val b = id("b")

    @Test
    fun `a new folder takes the app's place when it is a tile of its own`() {
        val layout = HomeLayout().add(FAVORITES, a).add(FAVORITES, b).createFolderWith(FAVORITES, "f", "Films", a)
        assertEquals(listOf(Folder("f", "Films", listOf(a)), AppItem(b)), layout.favorites.items)
    }

    @Test
    fun `an app in another folder or not in the section leaves it and gets a folder of its own`() {
        val inFolder = HomeLayout().add(FAVORITES, a).add(FAVORITES, b).createFolder(FAVORITES, "f", "Films", a)
            .addToFolder("f", b)
            .createFolderWith(FAVORITES, "g", "Music", b)
        assertEquals(listOf(Folder("f", "Films", listOf(a)), Folder("g", "Music", listOf(b))), inFolder.favorites.items)

        val absent = HomeLayout().createFolderWith(FAVORITES, "g", "Music", b)
        assertEquals(listOf(Folder("g", "Music", listOf(b))), absent.favorites.items)
    }

    @Test
    fun `folders are found with their section`() {
        val layout = HomeLayout().addCategory("c", "Kids").add("c", a).createFolder("c", "f", "Films", a)
        assertEquals(listOf("f"), layout.folders.map { it.id })
        assertEquals("c", layout.sectionOfFolder("f")?.id)
        assertNull(layout.sectionOfFolder("x"))
    }

    @Test
    fun `a folder as shown holds only installed apps`() {
        val layout = HomeLayout().add(FAVORITES, a).add(FAVORITES, b).createFolder(FAVORITES, "f", "Films", a)
            .addToFolder("f", b)
        val content = layout.arrange(listOf(App(a, "A", isTvApp = true)), Locale.ENGLISH)
        assertEquals(listOf("A"), content.folder("f")?.apps?.map { it.label })
        assertNull(content.folder("x"))
    }
}
