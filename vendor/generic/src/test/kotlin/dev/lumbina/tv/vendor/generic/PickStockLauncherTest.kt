package dev.lumbina.tv.vendor.generic

import dev.lumbina.tv.core.domain.app.AppId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickStockLauncherTest {

    private val lumbina = HomeCandidate(AppId("dev.lumbina.tv", "dev.lumbina.tv.MainActivity"), isSystemApp = false)
    private val googleTv = HomeCandidate(
        AppId("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity"),
        isSystemApp = true,
    )
    private val fallback = HomeCandidate(
        AppId("com.android.tv.settings", "com.android.tv.settings.system.FallbackHome"),
        isSystemApp = true,
    )
    private val projectivy = HomeCandidate(
        AppId("com.spocky.projengmenu", "com.spocky.projengmenu.ui.home.MainActivity"),
        isSystemApp = false,
    )

    @Test
    fun `google tv launcher is found among the emulator's home activities`() {
        assertEquals(googleTv.id, pickStockLauncher(listOf(lumbina, fallback, googleTv), "dev.lumbina.tv"))
    }

    @Test
    fun `third-party launchers and fallback home are not the stock launcher`() {
        assertNull(pickStockLauncher(listOf(lumbina, projectivy, fallback), "dev.lumbina.tv"))
    }
}
