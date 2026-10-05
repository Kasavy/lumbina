package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.common.LumbinaActions
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPageTest {

    @Test
    fun `pages opened from outside keep their names`() {
        assertEquals(SettingsPage.Rows, SettingsPage.valueOf(LumbinaActions.SETTINGS_PAGE_ROWS))
        assertEquals(SettingsPage.Phone, SettingsPage.valueOf(LumbinaActions.SETTINGS_PAGE_PHONE))
    }
}
