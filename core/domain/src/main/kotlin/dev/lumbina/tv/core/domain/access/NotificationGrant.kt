package dev.lumbina.tv.core.domain.access

import dev.lumbina.tv.core.domain.health.AccessibilityServiceList

/** Hidden in `Settings.Secure`, the same name since Android 4.3: the notification listeners the system binds. */
const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

/** The device's [ENABLED_NOTIFICATION_LISTENERS], colon-separated as the accessibility services' list. */
interface NotificationListeners {
    /** False until the user grants WRITE_SECURE_SETTINGS over adb. */
    val canWrite: Boolean

    fun get(): String?

    fun set(value: String)
}

/**
 * How Lumbina's notification listener ([listener], a flattened component) is let in, for the bell
 * and "now playing": firmware that needs Lumbina's connection to debugging has no screen where
 * the user could allow it. The way depends on the device's Android, chosen where the app is put
 * together.
 */
sealed interface NotificationGrant {
    /** Done before the shell's commands, by Lumbina itself where it can; nothing for most ways. */
    fun byItself() {}

    /** The shell's command, or null when there is nothing to do. */
    fun command(): String?

    /** Android 8.1 and newer. */
    class ByCommand(private val listener: String) : NotificationGrant {
        override fun command() = "cmd notification allow_listener $listener"
    }

    /**
     * Android 8.0 and older: `cmd notification` has no commands yet (they came in 8.1) and answers
     * with a success all the same. The system there follows [ENABLED_NOTIFICATION_LISTENERS] as it
     * changes, so the listener is added to that list: by Lumbina, with the permission it keeps, or
     * by the shell. Written only when the listener is missing; an entry of Lumbina's under another
     * name (an old version's) counts as missing and is replaced.
     */
    class BySetting(private val listener: String, private val listeners: NotificationListeners) : NotificationGrant {
        override fun byItself() {
            if (listeners.canWrite && missing()) listeners.set(withOurs())
        }

        // Quoted: a listener's class may be an inner one, its name with a "$" the shell would expand.
        override fun command(): String? =
            if (missing()) "settings put secure $ENABLED_NOTIFICATION_LISTENERS '${withOurs()}'" else null

        private fun missing(): Boolean = listeners.get().orEmpty().split(':').none { it == listener }

        /** The device's listeners with Lumbina's among them, the others kept. */
        private fun withOurs(): String =
            AccessibilityServiceList.join(AccessibilityServiceList.withoutOurs(listeners.get(), listener) + listener)
    }
}
