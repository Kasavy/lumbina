package dev.lumbina.tv.core.domain.phone

import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhonePageTest {

    @Test
    fun `a page switched off has no code, with a network and the service or without`() {
        assertEquals(PhonePage.Off, phonePage(enabled = false, serviceOn = true, ip = "192.168.1.5", key = "abc"))
        assertEquals(PhonePage.Off, phonePage(enabled = false, serviceOn = false, ip = null, key = "abc"))
    }

    @Test
    fun `a page on while the service is off has no code, as nothing would answer it`() {
        assertEquals(
            PhonePage.ServiceOff,
            phonePage(enabled = true, serviceOn = false, ip = "192.168.1.5", key = "abc"),
        )
    }

    @Test
    fun `a page on without a network address says so`() {
        assertEquals(PhonePage.NoNetwork, phonePage(enabled = true, serviceOn = true, ip = null, key = "abc"))
    }

    @Test
    fun `a page on with an address gives the page's address with its key`() {
        assertEquals(
            PhonePage.At("http://192.168.1.5:8765/?k=abc"),
            phonePage(enabled = true, serviceOn = true, ip = "192.168.1.5", key = "abc"),
        )
    }

    @Test
    fun `the link reads the settings, the service and the address as they are now`() = runTest {
        val settings = FakeSettings(PhoneRemoteSettings(enabled = true, key = "abc"))
        var bound = false
        val link = PhoneRemoteLink(settings, { "10.0.0.2" }, { bound })
        assertEquals(PhonePage.ServiceOff, link.current())
        bound = true
        assertEquals(PhonePage.At("http://10.0.0.2:8765/?k=abc"), link.current())
    }
}
