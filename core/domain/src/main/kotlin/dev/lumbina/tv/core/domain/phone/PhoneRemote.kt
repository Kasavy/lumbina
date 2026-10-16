package dev.lumbina.tv.core.domain.phone

import dev.lumbina.tv.core.domain.settings.SettingsRepository
import java.security.SecureRandom

/**
 * The phone remote: a web page Lumbina serves on the local network. [key] is part of the address
 * in the QR code, so only someone who saw the screen can use it; a new key locks old phones out.
 */
data class PhoneRemoteSettings(
    val enabled: Boolean = false,
    val key: String = "",
    /** The page's code is shown on the screensaver, for whoever is in the room: off unless asked for. */
    val onScreensaver: Boolean = false,
) {
    companion object {
        const val PORT = 8765

        /** The path the page's server gives the last diagnostics report at, and the file it is kept in for that. */
        const val REPORT_PATH = "/report"
        const val REPORT_FILE = "lumbina-report.txt"
        private const val KEY_LENGTH = 8
        private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

        fun newKey(): String {
            val random = SecureRandom()
            return String(CharArray(KEY_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })
        }
    }
}

/** Shared by the settings screen and the process that runs the page's server. */
typealias PhoneRemoteSettingsRepository = SettingsRepository<PhoneRemoteSettings>

/** The device's address on the local network (IPv4), or null when it has none. */
fun interface LocalAddress {
    fun ipv4(): String?
}

/** The page's address for the device's current local IP, or null when it has none. */
fun phoneRemoteAddress(ip: String?, key: String): String? = ip?.let { "http://$it:${PhoneRemoteSettings.PORT}/?k=$key" }

/** Where a phone on the same network gets the last diagnostics report, for the same key as the page. */
fun phoneReportAddress(ip: String?, key: String): String? =
    ip?.let { "http://$it:${PhoneRemoteSettings.PORT}${PhoneRemoteSettings.REPORT_PATH}?k=$key" }
