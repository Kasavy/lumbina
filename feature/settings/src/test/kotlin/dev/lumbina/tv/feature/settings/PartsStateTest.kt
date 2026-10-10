package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.device.FirmwarePart
import dev.lumbina.tv.core.domain.device.TurnedOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartsStateTest {

    private val mic = FirmwarePart("com.acme.mic", FirmwarePart.Feature.RemoteMic)
    private val sound = FirmwarePart("com.acme.volume", FirmwarePart.Feature.Volume)

    @Test
    fun `a part turned on stays in its place, on at once though the page still has it as off`() {
        val state = PartsState(canTurnOn = true).started(mic).finished(mic, TurnedOn.Done)

        assertEquals(listOf(PartRow(mic, isOff = false), PartRow(sound, isOff = true)), state.rows(listOf(mic, sound)))
        assertEquals(listOf(PartRow(mic, isOff = false), PartRow(sound, isOff = true)), state.rows(listOf(sound)))
        assertNull(state.working)
        assertNull(state.failure)
    }

    @Test
    fun `a failed try says how it failed until the next one starts`() {
        val refused = PartsState(canTurnOn = true).started(mic).finished(mic, TurnedOn.Refused)
        assertEquals(TurnedOn.Refused, refused.failure)
        assertEquals(listOf(PartRow(mic, isOff = true)), refused.rows(listOf(mic)))

        val again = refused.started(mic)
        assertEquals(mic, again.working)
        assertNull(again.failure)
        assertEquals(TurnedOn.NotConnected, again.finished(mic, TurnedOn.NotConnected).failure)
    }
}
