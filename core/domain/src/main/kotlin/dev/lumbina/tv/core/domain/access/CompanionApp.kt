package dev.lumbina.tv.core.domain.access

/**
 * An app Lumbina carries inside itself for devices that lack a part of the system, and installs
 * over its own adb ([LocalAccess]) with no dialog: the user agreed to it when connecting.
 */
interface CompanionApp {
    /**
     * Whether Lumbina has the app to install and the device wants it: it lacks the part, or has an
     * older copy. Asks the device and reads files: off the main thread.
     */
    suspend fun wanted(): Boolean

    /** Installs the app over [adb] and starts using it; false when that didn't work. */
    suspend fun install(adb: LocalAdb): Boolean
}
