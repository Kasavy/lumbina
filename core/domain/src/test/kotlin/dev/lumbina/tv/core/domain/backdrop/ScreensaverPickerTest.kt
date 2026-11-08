package dev.lumbina.tv.core.domain.backdrop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScreensaverPickerTest {

    @Test
    fun `the screensaver screen of its own comes first`() {
        assertEquals(
            ScreensaverPicker.Screen,
            screensaverPicker(screenOpens = true, tvSettings = true, googleTv = false),
        )
    }

    @Test
    fun `Android TV's own settings keep the list a few steps in`() {
        assertEquals(
            ScreensaverPicker.InSettings,
            screensaverPicker(screenOpens = false, tvSettings = true, googleTv = false),
        )
    }

    @Test
    fun `firmware's own settings and Google TV give nowhere to choose Lumbina`() {
        assertNull(screensaverPicker(screenOpens = false, tvSettings = false, googleTv = false))
        assertNull(screensaverPicker(screenOpens = true, tvSettings = true, googleTv = true))
    }
}
