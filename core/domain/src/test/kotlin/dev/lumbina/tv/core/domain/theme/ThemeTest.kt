package dev.lumbina.tv.core.domain.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeTest {

    @Test
    fun `every built-in theme can be read`() {
        Themes.builtIn.forEach { theme ->
            assertEquals(emptyList<Pair<ThemeRole, ThemeRole>>(), theme.unreadable(), theme.id)
        }
    }

    @Test
    fun `built-in themes have different ids`() {
        assertEquals(Themes.builtIn.size, Themes.builtIn.map { it.id }.distinct().size)
    }

    @Test
    fun `a theme that is gone falls back to the dark one`() {
        assertSame(Themes.Dark, Themes.byId("from a plug-in, removed"))
        assertSame(Themes.Dark, Themes.byId(null))
        assertSame(Themes.Light, Themes.byId("light"))
    }

    @Test
    fun `a theme without a value for a role is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            Themes.Dark.copy(id = "broken", colors = Themes.Dark.colors - ThemeRole.Warning)
        }
    }

    @Test
    fun `a theme whose text is the color of its panel is told apart`() {
        val grey = Themes.Dark.copy(id = "grey", colors = Themes.Dark.colors + (ThemeRole.Text to 0xFF22262E))
        assertTrue(ThemeRole.Text to ThemeRole.Panel in grey.unreadable())
    }

    @Test
    fun `contrast runs from one to twenty-one`() {
        assertEquals(1.0, contrast(0xFF777777, 0xFF777777), 0.001)
        assertEquals(21.0, contrast(0xFF000000, 0xFFFFFFFF), 0.001)
        assertEquals(contrast(0xFF102030, 0xFFE0E0E0), contrast(0xFFE0E0E0, 0xFF102030), 0.0)
    }
}
