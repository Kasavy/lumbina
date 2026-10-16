package dev.lumbina.tv.core.domain.remote

import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the remote's buttons do, as the user set it; shared by the settings and the remote's process. */
data class RemoteSettings(
    /** What the user changed in what the buttons do: see [KeyBindingPolicy.defaults]. */
    val bindings: List<KeyBinding> = emptyList(),
    /** The buttons the user made Lumbina's on its own screens only; in an app they are the device's. */
    val lumbinaOnly: Set<RemoteKey> = emptySet(),
    /** The buttons that are so by themselves, and that the user made Lumbina's everywhere. */
    val everywhere: Set<RemoteKey> = emptySet(),
    /** The names the user gave to buttons known only by their codes. */
    val keyNames: Map<RemoteKey, String> = emptyMap(),
    /** The other home screen Home's menu leads to; null, or one no longer there: the first found. */
    val otherHome: AppId? = null,
) {
    /**
     * A double press on Home shows the recent apps. Off, Home answers at once (it need not wait
     * for a second press), and the recent apps stay in its menu.
     */
    val doubleHomeRecents: Boolean
        get() = bindings.none { it.key == RemoteKey.Home && it.gesture == KeyGesture.DoubleTap && it.action == null }

    /** [gesture] on [key] does [action] from now on; null: as on the device. Setting a default back keeps no entry. */
    fun withBinding(key: RemoteKey, gesture: KeyGesture, action: RemoteAction?): RemoteSettings {
        val others = bindings.filterNot { it.key == key && it.gesture == gesture }
        // "As on the device" where Lumbina does nothing by itself is the usual state too.
        val isDefault = KeyBindingPolicy.defaultBindings(voiceAvailable = true)[key to gesture] == action
        return copy(bindings = if (isDefault) others else others + KeyBinding(key, gesture, action))
    }

    /** The buttons that are Lumbina's only on its own screens, the user's choices over what is so by itself. */
    val limited: Set<RemoteKey>
        get() = KeyBindingPolicy.DEFAULT_LUMBINA_ONLY - everywhere + lumbinaOnly - RemoteKey.Home

    fun withLumbinaOnly(key: RemoteKey, only: Boolean): RemoteSettings {
        val usual = key in KeyBindingPolicy.DEFAULT_LUMBINA_ONLY
        return copy(
            lumbinaOnly = if (only && !usual) lumbinaOnly + key else lumbinaOnly - key,
            everywhere = if (!only && usual) everywhere + key else everywhere - key,
        )
    }

    /** The user's own name for [key]; a blank one gives it back the name its code has. */
    fun withKeyName(key: RemoteKey, name: String): RemoteSettings =
        copy(keyNames = if (name.isBlank()) keyNames - key else keyNames + (key to name.trim()))

    /** As it was: nothing of the user's on [key], or on any key. */
    fun withoutBindings(key: RemoteKey? = null): RemoteSettings = copy(
        bindings = if (key == null) emptyList() else bindings.filterNot { it.key == key },
        keyNames = if (key == null) emptyMap() else keyNames - key,
        lumbinaOnly = if (key == null) emptySet() else lumbinaOnly - key,
        everywhere = if (key == null) emptySet() else everywhere - key,
    )
}

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
