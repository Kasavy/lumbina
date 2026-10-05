package dev.lumbina.tv.core.domain.device

/**
 * The volume people hear, on devices whose firmware applies its own: it keeps Android's music
 * stream at full and turns the sound down in its sound processor, so setting the stream volume
 * changes nothing audible there.
 */
fun interface DeviceVolume {
    /**
     * Raises (positive [percent]) or lowers the volume by [percent] points of 0–100; false when this
     * firmware has no such volume after all, so the caller uses Android's.
     */
    fun change(percent: Int): Boolean
}
