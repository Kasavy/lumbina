package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.LocalAdb
import dev.lumbina.tv.core.domain.access.SetupScreens
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.backdrop.ScreensaverPicker
import dev.lumbina.tv.core.domain.backdrop.ScreensaverSetup
import dev.lumbina.tv.core.domain.health.HealResult
import dev.lumbina.tv.core.domain.remote.KeyMap
import dev.lumbina.tv.core.testing.FakeSecureSettings
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeviceConditionsTest {

    private class Adb(private val state: AdbState) : LocalAdb {
        var asked = 0

        override suspend fun state(): AdbState = state.also { asked++ }

        override suspend fun connect() = state

        override suspend fun shell(command: String): String? = null
    }

    private class Screens(override val hasAccessibilitySettings: Boolean) : SetupScreens {
        override fun openAccessibilitySettings() = false

        override fun openDeveloperSettings() = false
    }

    private class Screensaver(override val picker: ScreensaverPicker?) : ScreensaverSetup {
        override val isLumbina = false

        override fun select() = false

        override fun openPicker() = false
    }

    private class Inputs(private val listed: List<InputSource>) : InputSources {
        override suspend fun showAndroid() = false

        override suspend fun list() = listed

        override suspend fun current(): String? = null

        override suspend fun open(id: String) = false
    }

    private open class Profile(
        override val id: String,
        override val localAdb: Boolean,
        override val setupOverAdb: Boolean,
        override val inputs: InputSources?,
    ) : DeviceProfile {
        override val priority = 0
        override val keyMap = KeyMap { _, _ -> null }
        override val stockLauncher: AppId? = null

        override fun matches(device: DeviceFingerprint) = true
    }

    private fun read(profile: DeviceProfile, adb: LocalAdb, a11yScreen: Boolean, picker: ScreensaverPicker?) =
        ReadDeviceConditions(
            profile,
            LocalAccess(adb, FakeSecureSettings(canWrite = false), { HealResult.AlreadyRunning }, "p", "p.L"),
            Screens(a11yScreen),
            FakeSecureSettings(canWrite = false),
            Screensaver(picker),
        )

    @Test
    fun `a box whose profile allows inputs but lists none has none, and its adb is asked once`() = runTest {
        val adb = Adb(AdbState.NotListening)
        val conditions = read(
            Profile("generic", localAdb = true, setupOverAdb = false, inputs = Inputs(emptyList())),
            adb,
            a11yScreen = true,
            picker = ScreensaverPicker.InSettings,
        )()
        assertEquals(AdbState.NotListening, conditions.debugging)
        assertEquals(1, adb.asked)
        assertFalse(conditions.inputs)
        assertEquals(ScreensaverPicker.InSettings, conditions.screensaverPicker)
        assertEquals(
            "generic · setup in settings · adb NotListening · a11y screen yes · secure settings no · " +
                "screensaver list InSettings · remote mic no · speech for apps no · mic to apps no · " +
                "volume hold yes · language first no · inputs no",
            conditions.summary(),
        )
    }

    @Test
    fun `a state the caller has is used as is, and a profile without own debugging says unused`() = runTest {
        val adb = Adb(AdbState.Off)
        val vidda = Profile(
            "hisense-vidaa",
            localAdb = true,
            setupOverAdb = true,
            inputs = Inputs(listOf(InputSource("1281", "HDMI 1"))),
        )
        val conditions = read(vidda, adb, a11yScreen = false, picker = null)(AdbState.Connected)
        assertEquals(AdbState.Connected, conditions.debugging)
        assertEquals(0, adb.asked)
        assertEquals(true, conditions.inputs)

        val noAdb = Profile("store", localAdb = false, setupOverAdb = false, inputs = null)
        assertNull(read(noAdb, adb, a11yScreen = true, picker = null)().debugging)
        assertEquals(0, adb.asked)
    }
}
