package dev.lumbina.tv.core.domain.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NetworkProblemTest {

    @Test
    fun `no network, then no internet, then a weak Wi-Fi signal`() {
        assertEquals(NetworkProblem.NoConnection, NetworkState.NONE.problem())
        assertEquals(NetworkProblem.NoInternet, NetworkState(NetworkKind.Wifi, signal = 4).problem())
        assertEquals(NetworkProblem.WeakSignal, NetworkState(NetworkKind.Wifi, signal = 1, online = true).problem())
    }

    @Test
    fun `a good or unknown signal and a cable are fine`() {
        assertNull(NetworkState(NetworkKind.Wifi, signal = 3, online = true).problem())
        assertNull(NetworkState(NetworkKind.Wifi, signal = null, online = true).problem())
        assertNull(NetworkState(NetworkKind.Ethernet, online = true).problem())
    }
}
