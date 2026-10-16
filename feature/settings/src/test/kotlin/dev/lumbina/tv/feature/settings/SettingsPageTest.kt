package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.common.LumbinaActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageTest {

    @Test
    fun `pages opened from outside keep their names`() {
        assertEquals(SettingsPage.Rows, SettingsPage.valueOf(LumbinaActions.SETTINGS_PAGE_ROWS))
        assertEquals(SettingsPage.Phone, SettingsPage.valueOf(LumbinaActions.SETTINGS_PAGE_PHONE))
        assertEquals(SettingsPage.Access, SettingsPage.valueOf(LumbinaActions.SETTINGS_PAGE_ACCESS))
        assertEquals(SettingsPage.Voice, SettingsPage.valueOf(LumbinaActions.SETTINGS_PAGE_VOICE))
        assertEquals(SettingsPage.State, SettingsPage.valueOf(LumbinaActions.SETTINGS_PAGE_STATE))
        assertEquals(SettingsPage.Widgets, SettingsPage.valueOf(LumbinaActions.SETTINGS_PAGE_WIDGETS))
        // The same extra carries it, and it must never be taken for a page.
        assertTrue(SettingsPage.entries.none { it.name == LumbinaActions.HOME_ALL_APPS })
    }

    @Test
    fun `a device without its own debugging and remote mic has no access and voice pages`() {
        assertEquals(setOf(SettingsPage.Access, SettingsPage.Voice), hiddenPages(localAdb = false, remoteMic = false))
        assertEquals(emptySet<SettingsPage>(), hiddenPages(localAdb = true, remoteMic = true))
        assertEquals(setOf(SettingsPage.Voice), hiddenPages(localAdb = true, remoteMic = false))
    }
}
