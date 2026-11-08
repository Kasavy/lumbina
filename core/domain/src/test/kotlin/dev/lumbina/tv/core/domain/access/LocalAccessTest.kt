package dev.lumbina.tv.core.domain.access

import dev.lumbina.tv.core.domain.health.HealAccessibilityService
import dev.lumbina.tv.core.domain.health.HealResult
import dev.lumbina.tv.core.testing.FakeHealLock
import dev.lumbina.tv.core.testing.FakeSecureSettings
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalAccessTest {

    private class FakeAdb(var state: AdbState, private val output: String = "") : LocalAdb {
        val commands = mutableListOf<String>()

        override suspend fun state() = state

        override suspend fun connect() = state

        override suspend fun shell(command: String): String? {
            if (state != AdbState.Connected) return null
            commands += command
            return output
        }
    }

    private val ours = "dev.lumbina.tv/.Service"

    private class FakeCompanion(private val wanted: Boolean) : CompanionApp {
        var installs = 0

        override suspend fun wanted() = wanted

        override suspend fun install(adb: LocalAdb): Boolean {
            installs++
            return true
        }
    }

    private fun access(
        adb: LocalAdb,
        settings: FakeSecureSettings = FakeSecureSettings(),
        companions: List<CompanionApp> = emptyList(),
        onHealed: (HealResult) -> Unit = {},
    ) = LocalAccess(
        adb,
        settings,
        HealAccessibilityService(settings, { false }, ours, FakeHealLock(), toggleDelayMillis = 0, settleMillis = 0)
            .let { heal -> suspend { heal().also(onHealed) } },
        "dev.lumbina.tv",
        "dev.lumbina.tv.Listener",
        companions,
    )

    @Test
    fun `connecting grants what Lumbina needs`() = runTest {
        val adb = FakeAdb(AdbState.Connected)
        assertEquals(SetupResult.Done, access(adb).connectAndSetUp())
        assertEquals(
            listOf(
                "pm grant dev.lumbina.tv android.permission.WRITE_SECURE_SETTINGS",
                "appops set dev.lumbina.tv GET_USAGE_STATS allow",
                "appops set dev.lumbina.tv ACCESS_RESTRICTED_SETTINGS allow",
                "cmd notification allow_listener dev.lumbina.tv/dev.lumbina.tv.Listener",
            ),
            adb.commands.flatMap { it.split(SHELL_AND_THEN) },
        )
    }

    @Test
    fun `a device whose debugging lets only a computer in is told apart from debugging off`() = runTest {
        assertEquals(SetupResult.NotListening, access(FakeAdb(AdbState.NotListening)).connectAndSetUp())
        assertEquals(SetupResult.Off, access(FakeAdb(AdbState.Off)).connectAndSetUp())
    }

    @Test
    fun `the commands for a computer give what connecting gives`() = runTest {
        val adb = FakeAdb(AdbState.Connected)
        access(adb).connectAndSetUp()
        val computer = access(FakeAdb(AdbState.NotListening)).computerCommands
        assertEquals(
            adb.commands.flatMap {
                it.split(SHELL_AND_THEN)
            }.map { "adb shell $it" }.toSet(),
            computer.toSet(),
        )
    }

    @Test
    fun `connecting installs the companion the device wants`() = runTest {
        val wanted = FakeCompanion(wanted = true)
        val unwanted = FakeCompanion(wanted = false)
        access(FakeAdb(AdbState.Connected), companions = listOf(wanted, unwanted)).connectAndSetUp()
        assertEquals(1, wanted.installs)
        assertEquals(0, unwanted.installs)
    }

    @Test
    fun `renewing sets up everything but the kept service, and only when connected`() = runTest {
        val adb = FakeAdb(AdbState.Connected)
        val companion = FakeCompanion(wanted = true)
        assertTrue(access(adb, companions = listOf(companion)).renew())
        assertEquals(
            listOf(
                "appops set dev.lumbina.tv GET_USAGE_STATS allow",
                // Those who connected before Lumbina lifted the restriction get it lifted too.
                "appops set dev.lumbina.tv ACCESS_RESTRICTED_SETTINGS allow",
                "cmd notification allow_listener dev.lumbina.tv/dev.lumbina.tv.Listener",
            ),
            adb.commands.flatMap { it.split(SHELL_AND_THEN) },
        )
        // In one connection: each costs a handshake.
        assertEquals(1, adb.commands.size)
        assertEquals(1, companion.installs)

        val away = FakeCompanion(wanted = true)
        assertFalse(access(FakeAdb(AdbState.NotAllowed), companions = listOf(away)).renew())
        assertEquals(0, away.installs)
    }

    @Test
    fun `connecting heals the service once and tells how it went`() = runTest {
        val settings = FakeSecureSettings(services = ours)
        val told = mutableListOf<HealResult>()
        access(FakeAdb(AdbState.Connected), settings, onHealed = { told += it }).connectAndSetUp()
        assertEquals(listOf("", ours), settings.writes)
        assertEquals(listOf<HealResult>(HealResult.Reenabled), told)
    }

    @Test
    fun `a key allowed for one connection only is told apart from a refused grant`() = runTest {
        val adb = object : LocalAdb {
            override suspend fun state() = AdbState.NotAllowed

            override suspend fun connect() = AdbState.Connected

            override suspend fun shell(command: String): String? = null
        }
        assertEquals(SetupResult.AllowedOnce, access(adb).connectAndSetUp())
    }

    @Test
    fun `a grant the device refuses is not taken for success and heals nothing`() = runTest {
        val settings = FakeSecureSettings(services = ours, canWrite = false)
        val adb = FakeAdb(AdbState.Connected, output = "Exception occurred while executing 'grant'")
        assertEquals(SetupResult.NotKept, access(adb, settings).connectAndSetUp())
        assertFalse(access(adb, settings).setKeepService(true))
        assertTrue(settings.writes.isEmpty())
    }

    @Test
    fun `keeping the service is the permission and the service, nothing else`() = runTest {
        val settings = FakeSecureSettings(services = ours)
        val adb = FakeAdb(AdbState.Connected)
        val companion = FakeCompanion(wanted = true)
        assertTrue(access(adb, settings, listOf(companion)).setKeepService(true))
        assertEquals(
            listOf(
                "pm grant dev.lumbina.tv android.permission.WRITE_SECURE_SETTINGS",
                "appops set dev.lumbina.tv ACCESS_RESTRICTED_SETTINGS allow",
            ),
            adb.commands,
        )
        assertEquals(listOf("", ours), settings.writes)
        assertEquals(0, companion.installs)
    }

    @Test
    fun `keeping the service needs a connection`() = runTest {
        val settings = FakeSecureSettings(services = ours)
        assertFalse(access(FakeAdb(AdbState.Off), settings).setKeepService(true))
        assertTrue(settings.writes.isEmpty())
    }

    @Test
    fun `nothing runs without access`() = runTest {
        val adb = FakeAdb(AdbState.NotAllowed)
        assertEquals(SetupResult.NotAllowed, access(adb).connectAndSetUp())
        assertEquals(SetupResult.Failed, access(FakeAdb(AdbState.Failed)).connectAndSetUp())
        assertFalse(access(adb).forceStop("com.example"))
    }

    @Test
    fun `Lumbina never stops itself`() = runTest {
        assertFalse(access(FakeAdb(AdbState.Connected)).forceStop("dev.lumbina.tv"))
    }

    @Test
    fun `computer commands keep the other services`() {
        val commands = ComputerSetup.commands("10.0.0.2", "a/.A:$ours", ours, "dev.lumbina.tv")
        assertEquals("adb connect 10.0.0.2", commands[0])
        assertEquals("adb shell appops set dev.lumbina.tv ACCESS_RESTRICTED_SETTINGS allow", commands[1])
        assertEquals("adb shell settings put secure enabled_accessibility_services a/.A:$ours", commands[2])
    }
}
