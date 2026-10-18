package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.data.home.decode
import dev.lumbina.tv.core.data.home.encode
import dev.lumbina.tv.core.domain.remote.KeyBinding
import dev.lumbina.tv.core.domain.remote.KeyGesture
import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.remote.RemoteKey
import dev.lumbina.tv.core.domain.remote.RemoteSettings
import kotlinx.serialization.Serializable

/**
 * [doubleHomeRecents] is what there was before bindings: a file that has it off and no bindings
 * yet is read as "a double press on Home: as on the device".
 */
@Serializable
internal data class StoredRemoteSettings(
    val doubleHomeRecents: Boolean = true,
    val otherHome: String? = null,
    val bindings: List<StoredKeyBinding> = emptyList(),
    val keyNames: Map<String, String> = emptyMap(),
    val lumbinaOnly: List<String> = emptyList(),
    val everywhere: List<String> = emptyList(),
    val volumeHold: Boolean = false,
)

/** [action]: an action's name, "app:" and an app's id, or null for "as on the device". */
@Serializable
internal data class StoredKeyBinding(val key: String, val gesture: String, val action: String? = null)

internal fun StoredRemoteSettings.toDomain(): RemoteSettings {
    val read = bindings.mapNotNull { stored ->
        val gesture = KeyGesture.entries.firstOrNull { it.name == stored.gesture } ?: return@mapNotNull null
        // An action this build does not know is dropped with its binding, not read as "as on the device".
        val action = stored.action?.let { decodeAction(it) ?: return@mapNotNull null }
        KeyBinding(RemoteKey(stored.key), gesture, action)
    }
    val migrated = if (!doubleHomeRecents &&
        read.none { it.key == RemoteKey.Home && it.gesture == KeyGesture.DoubleTap }
    ) {
        read + KeyBinding(RemoteKey.Home, KeyGesture.DoubleTap, action = null)
    } else {
        read
    }
    return RemoteSettings(
        bindings = migrated,
        lumbinaOnly = lumbinaOnly.map(::RemoteKey).toSet(),
        everywhere = everywhere.map(::RemoteKey).toSet(),
        keyNames = keyNames.mapKeys { RemoteKey(it.key) },
        otherHome = otherHome?.let(::decode),
        volumeHold = volumeHold,
    )
}

internal fun RemoteSettings.toStored() = StoredRemoteSettings(
    // Kept as it reads back, for a build from before bindings that finds this file.
    doubleHomeRecents = doubleHomeRecents,
    otherHome = otherHome?.encode(),
    // An action with no stored name is left out with its binding: written bare, it would read as "as on the device".
    bindings = bindings.mapNotNull { binding ->
        val action = binding.action?.let { encodeAction(it) ?: return@mapNotNull null }
        StoredKeyBinding(binding.key.id, binding.gesture.name, action)
    },
    keyNames = keyNames.mapKeys { it.key.id },
    lumbinaOnly = lumbinaOnly.map { it.id },
    everywhere = everywhere.map { it.id },
    volumeHold = volumeHold,
)

private const val APP = "app:"

/** The actions a user can bind, by the names they are stored under. */
private val NAMED: Map<String, RemoteAction> = mapOf(
    "lumbina" to RemoteAction.OpenLumbina,
    "all-apps" to RemoteAction.OpenAllApps,
    "recent-apps" to RemoteAction.ShowAppSwitcher,
    "quick-menu" to RemoteAction.ShowQuickMenu,
    "settings" to RemoteAction.OpenLumbinaSettings,
    "search" to RemoteAction.OpenSearch,
    "sleep-timer" to RemoteAction.ShowSleepTimer,
    "notifications" to RemoteAction.ShowNotifications,
    "screensaver" to RemoteAction.ShowScreensaver,
    "previous-app" to RemoteAction.OpenPreviousApp,
    "voice-language" to RemoteAction.CycleVoiceLanguage,
    "play-pause" to RemoteAction.MediaPlayPause,
    "next" to RemoteAction.MediaNext,
    "previous" to RemoteAction.MediaPrevious,
    "other-home" to RemoteAction.OpenOtherHome,
    "device-settings" to RemoteAction.OpenDeviceSettings,
    "screen-off" to RemoteAction.ScreenOff,
    "nothing" to RemoteAction.Nothing,
)

internal fun encodeAction(action: RemoteAction): String? = when (action) {
    is RemoteAction.OpenApp -> APP + action.app.encode()
    else -> NAMED.entries.firstOrNull { it.value == action }?.key
}

internal fun decodeAction(stored: String): RemoteAction? =
    if (stored.startsWith(APP)) decode(stored.removePrefix(APP))?.let(RemoteAction::OpenApp) else NAMED[stored]
