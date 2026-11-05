package dev.lumbina.tv.vendor.generic

import android.media.tv.TvInputManager.INPUT_STATE_CONNECTED
import android.media.tv.TvInputManager.INPUT_STATE_CONNECTED_STANDBY
import android.media.tv.TvInputManager.INPUT_STATE_DISCONNECTED
import dev.lumbina.tv.core.domain.device.InputPresence
import org.junit.Assert.assertEquals
import org.junit.Test

class PortPresenceTest {

    @Test
    fun `a device that is on works, named as it calls itself`() {
        val devices = listOf("Soundbar" to INPUT_STATE_CONNECTED_STANDBY, " MIBOX4 " to INPUT_STATE_CONNECTED)
        assertEquals(InputPresence.Working("MIBOX4"), portPresence(INPUT_STATE_CONNECTED, devices, tellsCables = false))
        assertEquals(
            InputPresence.Working(null),
            portPresence(INPUT_STATE_CONNECTED, listOf("" to INPUT_STATE_CONNECTED), tellsCables = false),
        )
    }

    @Test
    fun `a device asleep, or a cable alone, is only connected`() {
        assertEquals(
            InputPresence.Connected,
            portPresence(INPUT_STATE_CONNECTED, listOf("MIBOX4" to INPUT_STATE_CONNECTED_STANDBY), tellsCables = false),
        )
        assertEquals(InputPresence.Connected, portPresence(INPUT_STATE_CONNECTED, emptyList(), tellsCables = true))
        assertEquals(
            InputPresence.Connected,
            portPresence(INPUT_STATE_CONNECTED_STANDBY, emptyList(), tellsCables = true),
        )
    }

    @Test
    fun `a TV that never said a port is empty tells nothing by connected`() {
        assertEquals(null, portPresence(INPUT_STATE_CONNECTED, emptyList(), tellsCables = false))
    }

    @Test
    fun `nothing on the port is empty`() {
        assertEquals(InputPresence.Empty, portPresence(INPUT_STATE_DISCONNECTED, emptyList(), tellsCables = true))
        assertEquals(
            InputPresence.Empty,
            portPresence(INPUT_STATE_DISCONNECTED, listOf("old" to INPUT_STATE_DISCONNECTED), tellsCables = true),
        )
    }
}
