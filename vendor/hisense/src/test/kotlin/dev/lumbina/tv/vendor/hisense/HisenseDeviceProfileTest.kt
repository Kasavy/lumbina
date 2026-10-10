package dev.lumbina.tv.vendor.hisense

import android.content.ContextWrapper
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.FirmwarePart
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

    @Test
    fun `the launcher and the simple start page are home screens, the assistant of the same package is not`() {
        assertTrue(profile.isHomeScreen("com.jamdeo.tv.vod", "com.ju.main.launcher.LauncherActivity"))
        assertTrue(profile.isHomeScreen("com.jamdeo.tv.vod", null))
        assertTrue(profile.isHomeScreen("com.keylab.speech.view.vidaa", "com.ju.secondscreen.HomeActivity"))
        assertTrue(profile.isHomeScreen("com.keylab.speech.view.vidaa", null))
        assertTrue(profile.isHomeScreen("com.hisense.widget.pro", "com.ju.secondscreen.HomeActivity"))
        assertFalse(profile.isHomeScreen("com.hisense.widget.pro", null))
        assertFalse(profile.isHomeScreen("com.keylab.speech.view.vidaa", "com.hispeech.tv.ui.aiagent.AiAgentActivity"))
        assertFalse(profile.isHomeScreen("dev.lumbina.tv", null))
    }

    @Test
    fun `the remote's mic and the volume rest on packages of the firmware`() {
        assertEquals(
            listOf(
                FirmwarePart("com.hisense.pcm.share", FirmwarePart.Feature.RemoteMic),
                FirmwarePart("com.vt.mic.service", FirmwarePart.Feature.RemoteMic),
                FirmwarePart("com.hisense.tv.baseservice", FirmwarePart.Feature.Volume),
            ),
            profile.firmwareParts,
        )
    }

    private fun device(hasVidaaLauncher: Boolean) = object : DeviceFingerprint {
        override val manufacturer = "Hisense"
        override val model = "VIDAA_TV"
        override fun isInstalled(packageName: String) = hasVidaaLauncher && packageName == "com.jamdeo.tv.vod"
    }
}
