package dev.lumbina.tv.core.testing

import dev.lumbina.tv.core.domain.access.NotificationListeners

class FakeNotificationListeners(var value: String? = null, override var canWrite: Boolean = true) :
    NotificationListeners {
    override fun get(): String? = value

    override fun set(value: String) {
        this.value = value
    }
}
