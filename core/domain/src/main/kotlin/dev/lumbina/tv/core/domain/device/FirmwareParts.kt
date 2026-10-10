package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.access.LocalAdb

/** A package of the firmware that a feature of Lumbina rests on: with it turned off the feature is gone. */
data class FirmwarePart(val packageName: String, val serves: Feature) {
    enum class Feature { RemoteMic, Volume }
}

enum class PackageState {
    /** Not on the device, or not visible to Lumbina. */
    Missing,

    /** Uninstalled for this user; the system still holds it. */
    Removed,
    Disabled,
    Enabled,
}

/** Whether a package is on the device and turned on. */
fun interface PackageStates {
    fun of(packageName: String): PackageState
}

/** How a try to turn a part on ended. */
enum class TurnedOn { Done, NotConnected, Refused }

/**
 * The firmware's parts that Lumbina needs and that are turned off: the feature a part serves then
 * fails with nothing to say why (a remote that gives no sound to anyone). A part that is missing
 * altogether is another firmware and is not counted.
 */
class FirmwareParts(
    private val profile: DeviceProfile,
    private val packages: PackageStates,
    private val adb: LocalAdb,
) {
    fun off(): List<FirmwarePart> = profile.firmwareParts.filter(::isOff)

    fun isOff(feature: FirmwarePart.Feature): Boolean = profile.firmwareParts.any { it.serves == feature && isOff(it) }

    private fun isOff(part: FirmwarePart): Boolean =
        packages.of(part.packageName).let { it == PackageState.Disabled || it == PackageState.Removed }

    /** Lumbina can turn a part back on itself only over its own adb. */
    val canTurnOn: Boolean get() = profile.localAdb

    /**
     * Turns [part] back on over adb. The shell may be refused: a package the firmware itself keeps
     * disabled is not the shell's to enable. `pm` reports that as text, so the package's state tells.
     */
    suspend fun turnOn(part: FirmwarePart): TurnedOn {
        val removed = packages.of(part.packageName) == PackageState.Removed
        val command = if (removed) "pm install-existing ${part.packageName}" else "pm enable ${part.packageName}"
        adb.shell(command) ?: return TurnedOn.NotConnected
        return if (packages.of(part.packageName) == PackageState.Enabled) TurnedOn.Done else TurnedOn.Refused
    }
}
