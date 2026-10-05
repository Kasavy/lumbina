package dev.lumbina.tv.vendor.hisense

import android.content.ContextWrapper
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.remote.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HisenseDeviceProfileTest {

    private val profile = HisenseDeviceProfile(HisenseVolume(ContextWrapper(null)))

    @Test
    fun `remote home arrives as scan 102 or bare KEYCODE_F12`() {
        assertEquals(RemoteKey.Home, profile.keyMap.map(scanCode = 102, keyCode = 142))
        assertEquals(RemoteKey.Home, profile.keyMap.map(scanCode = 0, keyCode = 142))
    }

    @Test
    fun `mic arrives as scan 166 or vendor keycode 4381`() {
        assertEquals(RemoteKey.Mic, profile.keyMap.map(scanCode = 166, keyCode = 4381))
        assertEquals(RemoteKey.Mic, profile.keyMap.map(scanCode = 0, keyCode = 4381))
    }

    @Test
    fun `other keys are not ours`() {
        assertNull(profile.keyMap.map(scanCode = 105, keyCode = 21)) // D-pad left
        assertNull(profile.keyMap.map(scanCode = 158, keyCode = 4)) // Back
    }

    @Test
    fun `matches devices that have the VIDAA launcher`() {
        assertTrue(profile.matches(device(hasVidaaLauncher = true)))
        assertFalse(profile.matches(device(hasVidaaLauncher = false)))
    }

    private fun device(hasVidaaLauncher: Boolean) = object : DeviceFingerprint {
        override val manufacturer = "Hisense"
        override val model = "VIDAA_TV"
        override fun isInstalled(packageName: String) = hasVidaaLauncher && packageName == "com.jamdeo.tv.vod"
    }
}
