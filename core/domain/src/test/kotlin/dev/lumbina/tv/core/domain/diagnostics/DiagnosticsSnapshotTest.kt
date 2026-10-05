package dev.lumbina.tv.core.domain.diagnostics

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
}
