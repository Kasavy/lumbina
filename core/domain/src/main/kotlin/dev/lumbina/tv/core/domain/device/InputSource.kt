package dev.lumbina.tv.core.domain.device

/**
 * One of the device's video inputs: [id] as the firmware knows it, [name] as it calls it ("HDMI 1"),
 * and what is on it now where the device tells ([presence]; null where it does not, or was not asked).
 */
data class InputSource(val id: String, val name: String, val presence: InputPresence? = null) {
    /** [name], with the name of the device working on it: "HDMI 1 · MIBOX4". */
    val caption: String get() = inputCaption(name, presence)
}

/** What is on a video input now. */
sealed interface InputPresence {
    /** A device on it sends its picture; [device] is the name it sends with it, null where it sends none. */
    data class Working(val device: String?) : InputPresence

    /** A cable is in, the device at its end asleep or off. */
    data object Connected : InputPresence

    /** Nothing plugged in. */
    data object Empty : InputPresence
}

/** [name] with the working device's name after it, unless the user already named the input after it. */
fun inputCaption(name: String, presence: InputPresence?): String =
    deviceNote(name, presence)?.let { "$name · $it" } ?: name

/** The name a working device sends, unless the input's [name] has it already (named after it). */
fun deviceNote(name: String, presence: InputPresence?): String? =
    (presence as? InputPresence.Working)?.device?.takeUnless { name.contains(it, ignoreCase = true) }

/** Working inputs first, then those with a cable in (or not told), the empty ones last; else in the given order. */
fun List<InputSource>.byPresence(): List<InputSource> = sortedBy {
    when (it.presence) {
        is InputPresence.Working -> 0
        InputPresence.Empty -> 2
        else -> 1
    }
}
