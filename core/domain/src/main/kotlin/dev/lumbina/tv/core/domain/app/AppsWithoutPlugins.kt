package dev.lumbina.tv.core.domain.app

import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The app list without wallpaper plugins' settings screens: they are reached under Wallpaper and
 * are not apps. A plugin that names its settings activity hides only that one, so an app that
 * also carries a plugin stays; one that names none hides its whole package, as its launcher
 * entry can only be the plugin.
 */
class AppsWithoutPlugins(private val all: InstalledApps, private val plugins: WallpaperPlugins) : InstalledApps {
    override fun observe(): Flow<List<App>> = all.observe()
        .map { apps ->
            val (withSettings, without) = plugins.installed().partition { it.hasSettings }
            val screens = withSettings.mapNotNullTo(HashSet()) { p ->
                p.settingsActivity?.let { AppId(p.packageName, it) }
            }
            val packages = without.mapTo(HashSet()) { it.packageName }
            if (screens.isEmpty() && packages.isEmpty()) {
                apps
            } else {
                apps.filter { it.id !in screens && it.id.packageName !in packages }
            }
        }
        .flowOn(Dispatchers.IO)
}
