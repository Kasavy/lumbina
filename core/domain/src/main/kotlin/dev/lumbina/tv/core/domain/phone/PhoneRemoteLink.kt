package dev.lumbina.tv.core.domain.phone

import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Whether a phone can reach the phone remote page now, and at what address. */
sealed interface PhonePage {
    /** The page is switched off in the settings. */
    data object Off : PhonePage

    /** On, but the remote's service, which runs the page's server, is not running. */
    data object ServiceOff : PhonePage

    /** On, but the device has no local network address. */
    data object NoNetwork : PhonePage

    data class At(val address: String) : PhonePage
}

/** [ip]: the device's local address, null when it has none. */
fun phonePage(enabled: Boolean, serviceOn: Boolean, ip: String?, key: String): PhonePage = when {
    !enabled -> PhonePage.Off
    !serviceOn -> PhonePage.ServiceOff
    else -> phoneRemoteAddress(ip, key)?.let(PhonePage::At) ?: PhonePage.NoNetwork
}

/** The phone remote page as a phone would find it now. Asks the system: call it off the main thread. */
class PhoneRemoteLink @Inject constructor(
    private val settings: PhoneRemoteSettingsRepository,
    private val address: LocalAddress,
    private val service: AccessibilityHealth,
) {
    suspend fun current(): PhonePage {
        val remote = settings.settings.first()
        return phonePage(remote.enabled, service.isServiceBound(), address.ipv4(), remote.key)
    }
}
