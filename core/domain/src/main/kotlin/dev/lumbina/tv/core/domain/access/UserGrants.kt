package dev.lumbina.tv.core.domain.access

/** An access only the user can give Lumbina, in the system's settings. */
interface UserGrant {
    fun granted(): Boolean

    /**
     * Opens the screen where the user gives it, or as close as the firmware gets (the apps'
     * settings, where it is under special access); false when nothing opens.
     */
    fun openSettings(): Boolean
}

/** Reading notifications (Lumbina's notification listener). */
interface NotificationAccess : UserGrant

/** The device's usage statistics (usage access). */
interface UsageAccess : UserGrant
