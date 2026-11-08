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
        assertEquals(InputPresence.Working("MIBOX4"), portPresence(INPUT_STATE_CONNECTED, devices, PortSignals.None))
        assertEquals(
            InputPresence.Working(null),
            portPresence(INPUT_STATE_CONNECTED, listOf("" to INPUT_STATE_CONNECTED), PortSignals.None),
        )
    }

    @Test
    fun `a device asleep, or a cable alone, is only connected`() {
        assertEquals(
            InputPresence.Connected,
            portPresence(INPUT_STATE_CONNECTED, listOf("MIBOX4" to INPUT_STATE_CONNECTED_STANDBY), PortSignals.None),
        )
        assertEquals(InputPresence.Connected, portPresence(INPUT_STATE_CONNECTED, emptyList(), PortSignals.Cables))
        assertEquals(
            InputPresence.Connected,
            portPresence(INPUT_STATE_CONNECTED_STANDBY, emptyList(), PortSignals.Cables),
        )
    }

    @Test
    fun `a TV that never said a port is empty tells nothing by connected`() {
        assertEquals(null, portPresence(INPUT_STATE_CONNECTED, emptyList(), PortSignals.None))
    }

    @Test
    fun `nothing on the port is empty`() {
        assertEquals(InputPresence.Empty, portPresence(INPUT_STATE_DISCONNECTED, emptyList(), PortSignals.Cables))
        assertEquals(
            InputPresence.Empty,
            portPresence(INPUT_STATE_DISCONNECTED, listOf("old" to INPUT_STATE_DISCONNECTED), PortSignals.Cables),
        )
    }

    @Test
    fun `a TV that never says disconnected but puts ports without a signal in standby tells by the signal`() {
        val learnt = PortSignals.None.learn(listOf(INPUT_STATE_CONNECTED, INPUT_STATE_CONNECTED_STANDBY))
        assertEquals(PortSignals.Signal, learnt)
        assertEquals(InputPresence.Empty, portPresence(INPUT_STATE_CONNECTED_STANDBY, emptyList(), learnt))
        assertEquals(InputPresence.Connected, portPresence(INPUT_STATE_CONNECTED, emptyList(), learnt))
        // A CEC device asleep under a port in standby is still something on it.
        assertEquals(
            InputPresence.Connected,
            portPresence(INPUT_STATE_CONNECTED_STANDBY, listOf("MIBOX4" to INPUT_STATE_CONNECTED_STANDBY), learnt),
        )
    }

    @Test
    fun `what was learnt stays, and disconnected outranks the signal`() {
        assertEquals(PortSignals.None, PortSignals.None.learn(listOf(INPUT_STATE_CONNECTED, INPUT_STATE_CONNECTED)))
        assertEquals(PortSignals.Signal, PortSignals.Signal.learn(listOf(INPUT_STATE_CONNECTED)))
        assertEquals(PortSignals.Cables, PortSignals.Signal.learn(listOf(INPUT_STATE_DISCONNECTED)))
        assertEquals(
            PortSignals.Cables,
            PortSignals.Cables.learn(listOf(INPUT_STATE_CONNECTED, INPUT_STATE_CONNECTED_STANDBY)),
        )
        // Where empty ports say disconnected, standby is a cable with something asleep on it.
        assertEquals(
            InputPresence.Connected,
            portPresence(INPUT_STATE_CONNECTED_STANDBY, emptyList(), PortSignals.Cables),
        )
    }
}
