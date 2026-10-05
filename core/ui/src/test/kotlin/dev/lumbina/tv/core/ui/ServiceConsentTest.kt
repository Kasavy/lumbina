package dev.lumbina.tv.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceConsentTest {

    private val everywhere =
        listOf(R.string.service_consent_why, R.string.service_consent_sees, R.string.service_consent_speech)

    @Test
    fun `a device where the service only reads the remote is told the three things all are told`() {
        assertEquals(everywhere, consentParagraphs(ServiceScope()))
    }

    @Test
    fun `what the service does for other apps and over debugging is said where it does it`() {
        assertEquals(
            everywhere + R.string.service_consent_for_apps + R.string.service_consent_debugging,
            consentParagraphs(ServiceScope(speechForApps = true, localAdb = true)),
        )
        assertEquals(
            everywhere + R.string.service_consent_mic_apps,
            consentParagraphs(ServiceScope(feedsMicToApps = true)),
        )
    }
}
