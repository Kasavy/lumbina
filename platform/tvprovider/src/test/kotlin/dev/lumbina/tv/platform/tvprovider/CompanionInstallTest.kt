package dev.lumbina.tv.platform.tvprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionInstallTest {

    private val companion = AndroidTvChannels.COMPANION

    private fun wanted(provider: String?, installed: Long?, carried: Long? = 2, refused: Long? = null) =
        companionWanted(provider, { installed }, carried, refused)

    @Test
    fun `installed where there is no provider at all`() {
        assertTrue(wanted(provider = null, installed = null))
    }

    @Test
    fun `never over another app's provider`() {
        assertFalse(wanted(provider = "com.android.providers.tv", installed = null))
    }

    @Test
    fun `an older companion is updated, an equal or newer one left`() {
        assertTrue(wanted(companion, installed = 1))
        assertFalse(wanted(companion, installed = 2))
        assertFalse(wanted(companion, installed = 3))
    }

    @Test
    fun `an update pm refused waits for the next carried version`() {
        assertFalse(wanted(companion, installed = 1, refused = 2))
        assertTrue(wanted(companion, installed = 1, carried = 3, refused = 2))
    }

    @Test
    fun `nothing without a carried companion`() {
        assertFalse(wanted(provider = null, installed = null, carried = null))
    }

    @Test
    fun `visible to all apps from Android 11, the flag unknown before`() {
        assertEquals("cmd package install -r --force-queryable -S 42", companionInstallCommand(42, 30))
        assertEquals("cmd package install -r -S 42", companionInstallCommand(42, 29))
    }
}
