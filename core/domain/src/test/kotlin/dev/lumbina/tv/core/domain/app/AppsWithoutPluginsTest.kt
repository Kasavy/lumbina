package dev.lumbina.tv.core.domain.app

import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppsWithoutPluginsTest {

    private fun app(pkg: String) = App(AppId(pkg, "$pkg.Main"), pkg, isTvApp = true)

    private val plugins = object : WallpaperPlugins {
        override fun installed() = listOf(
            WallpaperPluginInfo("plugin.pkg/plugin.pkg.Service", "Plugin"),
            WallpaperPluginInfo("aerial/aerial.Plugin", "Aerial", settingsActivity = "aerial.PluginSettings"),
        )

        override suspend fun wallpapers(id: String): List<WallpaperItem> = emptyList()

        override fun openSettings(id: String) = false
    }

    @Test
    fun `wallpaper plugins are left out of the app list`() = runTest {
        val all = InstalledApps { flowOf(listOf(app("a"), app("plugin.pkg"), app("b"))) }
        val apps = AppsWithoutPlugins(all, plugins).observe().first()
        assertEquals(listOf("a", "b"), apps.map { it.id.packageName })
    }

    @Test
    fun `an app that carries a plugin stays, only the plugin's settings screen goes`() = runTest {
        val main = App(AppId("aerial", "aerial.Main"), "Aerial", isTvApp = true)
        val settings = App(AppId("aerial", "aerial.PluginSettings"), "Aerial plugin", isTvApp = true)
        val all = InstalledApps { flowOf(listOf(main, settings)) }
        assertEquals(listOf(main), AppsWithoutPlugins(all, plugins).observe().first())
    }
}
