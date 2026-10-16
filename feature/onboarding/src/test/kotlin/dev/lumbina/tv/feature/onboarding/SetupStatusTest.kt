package dev.lumbina.tv.feature.onboarding

import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.speech.ModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStatusTest {

    private val installed = VoiceSetup(mapOf("ru" to ModelState.Installed, "en" to ModelState.Installed), 0)
    private val downloading = VoiceSetup(mapOf("ru" to ModelState.Installed, "en" to ModelState.Downloading(1, 2)), 2)

    @Test
    fun `without a remote mic voice is no step`() {
        assertTrue(SetupStatus(serviceRunning = true, voice = null).complete)
        assertFalse(SetupStatus(serviceRunning = false, voice = null).complete)
    }

    @Test
    fun `with a remote mic every speech model must be installed`() {
        assertTrue(SetupStatus(serviceRunning = true, voice = installed).complete)
        assertFalse(SetupStatus(serviceRunning = true, voice = downloading).complete)
        assertTrue(downloading.busy)
        assertFalse(installed.busy)
    }

    @Test
    fun `steps count what is left`() {
        assertEquals(2, SetupStatus(serviceRunning = false, voice = downloading).steps)
        assertEquals(1, SetupStatus(serviceRunning = false, voice = installed).steps)
        assertEquals(1, SetupStatus(serviceRunning = true, voice = downloading).steps)
    }

    @Test
    fun `the service Lumbina is bringing back itself is no step of the user's`() {
        assertEquals(1, SetupStatus(serviceRunning = false, voice = downloading, starting = true).userSteps)
        assertEquals(2, SetupStatus(serviceRunning = false, voice = downloading).userSteps)
        assertEquals(0, SetupStatus(serviceRunning = false, starting = true).userSteps)
    }

    @Test
    fun `channels and notifications not allowed yet are steps too`() {
        val asked =
            SetupStatus(serviceRunning = true, voice = installed, channels = "permission", noticesMissing = true)
        assertEquals(2, asked.steps)
        assertFalse(asked.complete)
        assertFalse(SetupStatus(serviceRunning = true, noticesMissing = true).complete)
        assertTrue(SetupStatus(serviceRunning = true).complete)
    }

    @Test
    fun `debugging counts as on once known and not off`() {
        assertFalse(SetupStatus(serviceRunning = false).debuggingOn)
        assertFalse(SetupStatus(serviceRunning = false, adb = AdbState.Off).debuggingOn)
        assertTrue(SetupStatus(serviceRunning = false, adb = AdbState.NotAllowed).debuggingOn)
        assertTrue(SetupStatus(serviceRunning = false, adb = AdbState.Connected).debuggingOn)
    }
}
