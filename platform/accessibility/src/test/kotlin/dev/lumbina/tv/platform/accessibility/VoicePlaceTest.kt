package dev.lumbina.tv.platform.accessibility

import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePlaceTest {

    @Test
    fun `a reason the user can fix sends OK to the page where it is fixed`() {
        assertEquals(LumbinaActions.SETTINGS_PAGE_PHONE, placeOf(SpeechUnavailable.Reason.NoKey)?.page())
        assertEquals(LumbinaActions.SETTINGS_PAGE_VOICE, placeOf(SpeechUnavailable.Reason.Region)?.page())
    }

    @Test
    fun `a reason on the service's side sends OK nowhere`() {
        assertNull(placeOf(SpeechUnavailable.Reason.Busy))
        assertNull(placeOf(SpeechUnavailable.Reason.ServiceError))
    }

    @Test
    fun `home is not a settings page`() {
        assertNull(VoiceController.Place.Home.page())
    }
}
