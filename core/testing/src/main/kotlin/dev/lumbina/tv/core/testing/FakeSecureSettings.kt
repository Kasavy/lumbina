package dev.lumbina.tv.core.testing

import dev.lumbina.tv.core.domain.health.SecureSettings

class FakeSecureSettings(var services: String? = null, override var canWrite: Boolean = true) : SecureSettings {
    /** Every value written to the services list, in order. */
    val writes = mutableListOf<String>()
    var accessibilityEnabled = false
        private set

    override fun enabledAccessibilityServices(): String? = services

    override fun setEnabledAccessibilityServices(value: String) {
        services = value
        writes += value
    }

    override fun setAccessibilityEnabled() {
        accessibilityEnabled = true
    }
}
