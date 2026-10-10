package dev.lumbina.tv.core.domain.remote

import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the remote's Home key does, as the user set it; shared by the settings and the remote's process. */
data class RemoteSettings(
    /**
     * A double press on Home shows the recent apps. Off, Home answers at once (it need not wait
     * for a second press), and the recent apps stay in its menu.
     */
    val doubleHomeRecents: Boolean = true,
    /** The other home screen Home's menu leads to; null, or one no longer there: the first found. */
    val otherHome: AppId? = null,
)

typealias RemoteSettingsRepository = SettingsRepository<RemoteSettings>

/** A home screen on the device other than Lumbina, under the name to show for it. */
data class HomeScreen(val id: AppId, val label: String)

/** The home screens the device has besides Lumbina, the firmware's own first. */
fun interface HomeScreens {
    suspend fun installed(): List<HomeScreen>
}

/**
 * Whether an activity that declared itself a home screen is only a stand-in for one: the settings
 * app's FallbackHome (shown while the real launcher is unavailable) or the first-run setup.
 */
fun AppId.isStandInHome(): Boolean = activityName.endsWith(".FallbackHome") || packageName in SETUP_PACKAGES

private val SETUP_PACKAGES =
    setOf("com.android.provision", "com.google.android.setupwizard", "com.google.android.tungsten.setupwraith")

/** What there is to choose from, and the one in use; [chosen] is null only when there is none. */
data class OtherHomeChoice(val screens: List<HomeScreen>, val chosen: HomeScreen?)

/**
 * The other home screen, which Home's menu leads to: the firmware's settings and inputs are
 * reached through it. A firmware may have several (a full launcher and a simple start page), and
 * the user may have installed more, so it is chosen among those the system lists.
 */
class OtherHome @Inject constructor(
    private val screens: HomeScreens,
    private val settings: RemoteSettingsRepository,
    apps: InstalledApps,
) {
    /** Looked up again when apps change: a launcher may be installed or removed while this is watched. */
    val choice: Flow<OtherHomeChoice> = combine(
        settings.settings.map { it.otherHome }.distinctUntilChanged(),
        apps.observe(),
    ) { chosen, _ ->
        val installed = screens.installed()
        OtherHomeChoice(installed, installed.firstOrNull { it.id == chosen } ?: installed.firstOrNull())
    }

    suspend fun use(screen: HomeScreen) {
        settings.update { it.copy(otherHome = screen.id) }
    }
}
