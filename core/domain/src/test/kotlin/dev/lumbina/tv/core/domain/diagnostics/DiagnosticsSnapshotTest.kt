package dev.lumbina.tv.core.domain.diagnostics

import dev.lumbina.tv.core.domain.device.DeviceAnswer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsSnapshotTest {

    private val system: SystemFacts = object : SystemInfo {
        override val appVersion = "0.0.1"
        override val debugBuild = true
        override val manufacturer = "Acme"
        override val model = "TV_1"
        override val androidRelease = "14"
        override val sdkInt = 34
        override val totalRamBytes = 2048L * 1024 * 1024
        override val availableRamBytes = 512L * 1024 * 1024
        override val freeStorageBytes = 3000L * 1024 * 1024
        override val firmware = "V0000.07.02A"
        override val fingerprint = "acme/tv/tv:14/UP1A/1:user/release-keys"
        override val hardware = "board, chip"
        override val uptimeMillis = 185_000L
        override val inputDevices = listOf("Acme Remote (000d:3838)")
        override val homeApp = "com.acme.launcher"
        override val microphoneGranted = false
        override val processes = listOf("dev.lumbina.tv:ui")
        override val processExits = listOf("10-07 12:00:00 dev.lumbina.tv:remote: signaled")

        override fun packageVersion(packageName: String): String? = null
    }.snapshot()

    private val snapshot = DiagnosticsSnapshot(
        system = system,
        deviceProfile = "acme-tv",
        stockLauncher = null,
        serviceRunning = false,
        canHealService = false,
        remoteMic = true,
        voiceLanguages = listOf("ru", "en"),
        installedModels = emptyList(),
        voiceMode = "Auto",
        lumbinaKeyboard = true,
        installedApps = 30,
        favorites = 2,
        categories = 1,
        hiddenApps = 0,
        access = AccessFacts(debugging = "Off", openOnPowerOn = false, notifications = true, usage = false),
        channels = ChannelsFacts(provider = null, permissionMissing = false, channels = 0, programs = 0, watchNext = 0),
        firmwarePackages = listOf("com.acme.mic" to null, "com.acme.launcher" to "1.2"),
    )

    @Test
    fun `the report states problems plainly and ends with the log`() {
        val report = snapshot.toReport("<time>", listOf("line 1", "line 2"))
        assertTrue("Version: 0.0.1 (debug)" in report)
        assertTrue("Accessibility service: NOT running" in report)
        assertTrue("Installed models: none" in report)
        assertTrue("RAM: 512 MB free of 2048 MB" in report)
        assertTrue("Stock launcher: none" in report)
        assertTrue(report.trimEnd().endsWith("line 1\nline 2"))
    }

    @Test
    fun `the report says what the device is and who its remote is`() {
        val report = snapshot.toReport("<time>", emptyList())
        assertTrue("Firmware: V0000.07.02A" in report)
        assertTrue("Hardware: board, chip" in report)
        assertTrue("Up: 3 min" in report)
        assertTrue("Input device: Acme Remote (000d:3838)" in report)
        assertTrue("com.acme.mic: NOT installed" in report)
        assertTrue("com.acme.launcher: 1.2" in report)
    }

    @Test
    fun `the report says why Lumbina would not open, hear or stay`() {
        val report = snapshot.toReport("<time>", emptyList())
        assertTrue("Open on power-on: nothing" in report)
        assertTrue("Home app: com.acme.launcher" in report)
        assertTrue("Microphone permission: NOT granted" in report)
        assertTrue("Debugging connection: Off" in report)
        assertTrue("Notification access: granted" in report)
        assertTrue("Usage access: NOT granted" in report)
        assertTrue("Running: dev.lumbina.tv:ui" in report)
        assertTrue("Ended: 10-07 12:00:00 dev.lumbina.tv:remote: signaled" in report)
    }

    @Test
    fun `the report carries what the device answered, before the log`() {
        val report = snapshot.toReport(
            "<time>",
            listOf("line 1"),
            listOf(
                DeviceAnswer("pm list packages -d", "package:a\n"),
                DeviceAnswer("ping", " "),
            ),
        )
        assertTrue("[Asked of the device]\n> pm list packages -d\npackage:a\n> ping\n(nothing)\n" in report)
        assertTrue(report.indexOf("[Asked of the device]") < report.indexOf("Recent log:"))
        assertTrue("[Asked of the device]" !in snapshot.toReport("<time>", emptyList()))
    }

    @Test
    fun `the report says where channels would come from`() {
        val none = snapshot.toReport("<time>", emptyList())
        assertTrue("TV provider: none" in none)

        val some = snapshot.copy(
            channels = ChannelsFacts("org.provider", permissionMissing = true, channels = 2, programs = 9, 1),
        ).toReport("<time>", emptyList())
        assertTrue("TV provider: org.provider" in some)
        assertTrue("Listings permission: NOT granted" in some)
        assertTrue("Channels: 2 (9 programs)" in some)
        assertTrue("Continue watching: 1" in some)
    }
}
