package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.device.FirmwarePart
import dev.lumbina.tv.core.domain.device.TurnedOn

/** A part of the firmware as the diagnostics page shows it: turned off still, or turned on from the page. */
data class PartRow(val part: FirmwarePart, val isOff: Boolean)

/**
 * What the diagnostics page knows about turning the firmware's parts back on, besides which are off
 * (that comes with the rest of the page's state, re-read every few seconds, so it may lag behind).
 */
data class PartsState(
    /** Lumbina can turn a part on itself here. */
    val canTurnOn: Boolean,
    /** Turned on from the page since it was opened. */
    val turnedOn: List<FirmwarePart> = emptyList(),
    /** The one being turned on now: further presses are ignored. */
    val working: FirmwarePart? = null,
    /** How the last try failed; null when it didn't, or none was made. */
    val failure: TurnedOn? = null,
) {
    fun started(part: FirmwarePart): PartsState = copy(working = part, failure = null)

    fun finished(part: FirmwarePart, result: TurnedOn): PartsState = copy(
        working = null,
        turnedOn = if (result == TurnedOn.Done) turnedOn + part else turnedOn,
        failure = result.takeIf { it != TurnedOn.Done },
    )

    /** The rows for the parts that were [off] when the page last looked. */
    fun rows(off: List<FirmwarePart>): List<PartRow> = (off + turnedOn)
        .distinctBy { it.packageName }
        .sortedBy { it.packageName }
        .map { PartRow(it, isOff = it !in turnedOn) }
}
