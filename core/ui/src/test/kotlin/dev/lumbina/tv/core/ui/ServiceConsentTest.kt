package dev.lumbina.tv.core.ui

import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.remote.KeyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceConsentTest {

    private val withMic =
        listOf(R.string.service_consent_why, R.string.service_consent_sees, R.string.service_consent_speech)

    @Test
    fun `a device without a remote mic is told nothing about voice`() {
        assertEquals(
            listOf(R.string.service_consent_why_no_mic, R.string.service_consent_sees_no_mic),
            consentParagraphs(ServiceScope()),
        )
    }

    @Test
    fun `a device with a remote mic is told about voice input and where speech goes`() {
        assertEquals(withMic, consentParagraphs(ServiceScope(remoteMic = true)))
    }

    @Test
    fun `what the service does for other apps and over debugging is said where it does it`() {
        assertEquals(
            withMic + R.string.service_consent_for_apps + R.string.service_consent_debugging,
            consentParagraphs(ServiceScope(remoteMic = true, speechForApps = true, localAdb = true)),
        )
        assertEquals(
            withMic + R.string.service_consent_mic_apps,
            consentParagraphs(ServiceScope(remoteMic = true, feedsMicToApps = true)),
        )
    }

    private fun profile(setupOverAdb: Boolean) = object : DeviceProfile {
        override val id = "acme"
        override val priority = 0
        override val keyMap = KeyMap { _, _ -> null }
        override val stockLauncher: AppId? = null
        override val localAdb = true
        override val setupOverAdb = setupOverAdb

        override fun matches(device: DeviceFingerprint) = true
    }

    @Test
    fun `debugging is in the consent only where the setup goes that way`() {
        assertTrue(profile(setupOverAdb = true).serviceScope().localAdb)
        // A TV where the connection is an option the setup doesn't take.
        assertFalse(profile(setupOverAdb = false).serviceScope().localAdb)
    }
}
