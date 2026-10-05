package dev.lumbina.tv.core.domain.access

/**
 * An app Lumbina carries inside itself for devices that lack a part of the system, and installs
 * over its own adb ([LocalAccess]) with no dialog: the user agreed to it when connecting.
 */
interface CompanionApp {
    /** Whether the device lacks the part and Lumbina has the app to install. */
    fun missing(): Boolean

    /** Installs the app over [adb] and starts using it; false when that didn't work. */
    suspend fun install(adb: LocalAdb): Boolean
}
