package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAdb
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.remote.KeyMap
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirmwarePartsTest {

    private val mic = FirmwarePart("com.acme.mic", FirmwarePart.Feature.RemoteMic)
    private val sound = FirmwarePart("com.acme.volume", FirmwarePart.Feature.Volume)

    private val profile = object : DeviceProfile {
        override val id = "acme"
        override val priority = 0
        override val keyMap = KeyMap { _, _ -> null }
        override val stockLauncher: AppId? = null
        override val localAdb = true
        override val firmwareParts: List<FirmwarePart> get() = listOf(mic, sound)

        override fun matches(device: DeviceFingerprint) = true
    }

    /** The shell turns a package on when [connected] and the device lets it ([obeys]). */
    private class Device(var connected: Boolean = true, var obeys: Boolean = true) :
        LocalAdb,
        PackageStates {
        val states = mutableMapOf<String, PackageState>()
        val commands = mutableListOf<String>()

        override fun of(packageName: String) = states[packageName] ?: PackageState.Missing

        override suspend fun state() = if (connected) AdbState.Connected else AdbState.Off

        override suspend fun connect() = state()

        override suspend fun shell(command: String): String? {
            if (!connected) return null
            commands += command
            if (obeys) states[command.substringAfterLast(' ')] = PackageState.Enabled
            return ""
        }
    }

    @Test
    fun `the parts turned off or removed count, a missing one is another firmware`() {
        val device = Device()
        device.states[mic.packageName] = PackageState.Disabled
        val parts = FirmwareParts(profile, device, device)

        assertEquals(listOf(mic), parts.off())
        assertTrue(parts.isOff(FirmwarePart.Feature.RemoteMic))
        assertFalse(parts.isOff(FirmwarePart.Feature.Volume))

        device.states[sound.packageName] = PackageState.Removed
        assertEquals(listOf(mic, sound), parts.off())
    }

    @Test
    fun `a disabled part is enabled and a removed one installed again`() = runTest {
        val device = Device()
        device.states[mic.packageName] = PackageState.Disabled
        device.states[sound.packageName] = PackageState.Removed
        val parts = FirmwareParts(profile, device, device)

        assertEquals(TurnedOn.Done, parts.turnOn(mic))
        assertEquals(TurnedOn.Done, parts.turnOn(sound))
        assertEquals(listOf("pm enable com.acme.mic", "pm install-existing com.acme.volume"), device.commands)
        assertEquals(emptyList<FirmwarePart>(), parts.off())
    }

    @Test
    fun `no connection and a refusal are told apart`() = runTest {
        val device = Device(obeys = false)
        device.states[mic.packageName] = PackageState.Disabled
        val parts = FirmwareParts(profile, device, device)

        assertEquals(TurnedOn.Refused, parts.turnOn(mic))
        device.connected = false
        assertEquals(TurnedOn.NotConnected, parts.turnOn(mic))
    }
}
