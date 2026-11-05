package dev.lumbina.tv.core.domain.device

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

    /**
     * What is on each input now, by [InputSource.id]: a device working, a cable only, nothing. An
     * input the device can't tell of is left out; on such a device, all of them.
     */
    suspend fun presence(): Map<String, InputPresence> = emptyMap()

    /** Whether [packageName] is the firmware's own screen that [open] shows an input on. */
    fun showsInputs(packageName: String): Boolean = false
}
