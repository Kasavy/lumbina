package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.remote.RemoteKey
import dev.lumbina.tv.core.domain.remote.RemoteSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonActionsTest {

    private fun state(closeApps: Boolean) = ButtonsState(
        RemoteSettings(),
        apps = emptyList(),
        otherHome = null,
        deviceSettings = false,
        closeApps = closeApps,
    )

    @Test
    fun `closing every app is offered only where Lumbina can close apps`() {
        val red = RemoteKey.ofCode(183)
        assertTrue(RemoteAction.CloseAllApps in state(closeApps = true).actionsOf(ActionKind.Device, red))
        assertFalse(RemoteAction.CloseAllApps in state(closeApps = false).actionsOf(ActionKind.Device, red))
    }
}
