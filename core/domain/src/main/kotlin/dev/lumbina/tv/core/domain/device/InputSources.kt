package dev.lumbina.tv.core.domain.device

/** One of the device's video inputs: [id] as the firmware knows it, [name] as it calls it ("HDMI 1"). */
data class InputSource(val id: String, val name: String)

/** The device's video inputs (HDMI and the like), where an app can choose between them. */
interface InputSources {
    /** Makes Android's own picture the chosen one, if an input is; true when it had to switch. */
    suspend fun showAndroid(): Boolean

    /** The inputs besides Android's own picture, in the firmware's order; empty where it can't tell. */
    suspend fun list(): List<InputSource>

    /** The chosen input's id; null when it is Android's own picture or can't be told. */
    suspend fun current(): String?

    /** Chooses input [id] and shows it full screen; false when it could not. */
    suspend fun open(id: String): Boolean

    /** Whether [packageName] is the firmware's own screen that [open] shows an input on. */
    fun showsInputs(packageName: String): Boolean = false
}
