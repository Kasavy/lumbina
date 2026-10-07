package dev.lumbina.tv.core.domain.diagnostics

import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAdb
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.DeviceAnswer
import dev.lumbina.tv.core.domain.device.DeviceDiagnostics
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.remote.KeyMap
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AskDeviceTest {

    /** Answers each command with what [answer] gives for it; null as a lost connection. */
    private class FakeAdb(var state: AdbState = AdbState.Connected, val answer: (String) -> String? = { "ok" }) :
        LocalAdb {
        val commands = mutableListOf<String>()

        override suspend fun state() = state

        override suspend fun connect() = state

        override suspend fun shell(command: String): String? {
            commands += command
            return answer(command)
        }
    }

    private class Profile(override val localAdb: Boolean, override val diagnostics: DeviceDiagnostics?) :
        DeviceProfile {
        override val id = "test"
        override val priority = 0
        override val keyMap = KeyMap { _, _ -> null }
        override val stockLauncher: AppId? = null

        override fun matches(device: DeviceFingerprint) = true
    }

    private val firmware = object : DeviceDiagnostics {
        override val shellCommands = listOf("dumpsys firmware")

        override fun probe() = listOf(DeviceAnswer("service: hello", "OK"))
    }

    @Test
    fun `the firmware's services are asked first, then the shell, its common commands first`() = runTest {
        val adb = FakeAdb()
        val answers = AskDevice(Profile(localAdb = true, firmware), adb, StandardTestDispatcher(testScheduler))()

        assertEquals(DeviceAnswer("service: hello", "OK"), answers.first())
        assertEquals(listOf("pm list packages -d", "dumpsys firmware"), adb.commands.takeLast(2))
        assertEquals(adb.commands, answers.drop(1).map { it.question })
    }

    @Test
    fun `without a shell of its own, or without a connection, the shell is not asked`() = runTest {
        val unused = FakeAdb()
        val probeOnly = AskDevice(Profile(localAdb = false, firmware), unused, StandardTestDispatcher(testScheduler))()
        assertEquals(listOf(DeviceAnswer("service: hello", "OK")), probeOnly)

        val off = FakeAdb(AdbState.NotAllowed)
        val answers = AskDevice(Profile(localAdb = true, null), off, StandardTestDispatcher(testScheduler))()
        assertEquals(listOf(DeviceAnswer("shell", "not asked: debugging connection NotAllowed")), answers)
        assertTrue(unused.commands.isEmpty() && off.commands.isEmpty())
    }

    @Test
    fun `a command that loses the connection is the last one asked`() = runTest {
        val adb = FakeAdb { null }
        val answers = AskDevice(Profile(localAdb = true, firmware), adb, StandardTestDispatcher(testScheduler))()

        assertEquals(1, adb.commands.size)
        assertEquals("no connection", answers.last().answer)
    }

    @Test
    fun `a long answer is cut to its head, and one that fits is left whole`() = runTest {
        val fits = (1..60).joinToString("\n") { "line $it" } + "\n"
        val long = (1..61).joinToString("\n") { "line $it" }
        val adb = FakeAdb { if (it == "pm list packages -d") long else fits }
        val answers = AskDevice(Profile(localAdb = true, null), adb, StandardTestDispatcher(testScheduler))()

        assertEquals(fits, answers.first().answer)
        assertEquals(61, answers.last().answer.lines().size)
        assertTrue(answers.last().answer.endsWith("line 60\n… 61 lines"))
    }

    @Test
    fun `a probe that hangs is left behind, and the report goes on`() = runTest {
        val hangs = object : DeviceDiagnostics {
            override fun probe(): List<DeviceAnswer> {
                Thread.sleep(2_000)
                return emptyList()
            }
        }
        val answers = AskDevice(Profile(localAdb = false, hangs), FakeAdb(), probeMillis = 1_000)()

        assertEquals(listOf(DeviceAnswer("probe", "no answer in 1 s")), answers)
    }
}
