package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.domain.remote.KeyBindingPolicy
import dev.lumbina.tv.core.domain.remote.KeyCapture
import dev.lumbina.tv.core.domain.remote.KeyGesture
import dev.lumbina.tv.core.domain.remote.OtherHome
import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.remote.RemoteKey
import dev.lumbina.tv.core.domain.remote.RemoteSettings
import dev.lumbina.tv.core.domain.remote.RemoteSettingsRepository
import dev.lumbina.tv.core.domain.status.DeviceScreens
import dev.lumbina.tv.core.domain.text.Search
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the buttons' pages show: the user's [settings], the [apps] a button can open, and what the
 * device has for the "device" actions: another home screen ([otherHome]), settings of its own.
 */
data class ButtonsState(
    val settings: RemoteSettings,
    val apps: List<App>,
    val otherHome: HomeScreen?,
    val deviceSettings: Boolean,
    /** Whether home has a search to open. */
    val search: Boolean = false,
    /** Whether the remote has a mic button Lumbina hears: it has a row of its own then. */
    val mic: Boolean = false,
    /** Whether the remote's service runs: without it no button does what is set here. */
    val serviceOn: Boolean = true,
    /** Whether a held volume button needs [RemoteSettings.volumeHold] here ([DeviceProfile.heldVolumeStops]). */
    val volumeHoldable: Boolean = false,
) {
    /**
     * The buttons that do something of Lumbina's or were set by the user, besides Home: each has a
     * row of its own. Those Lumbina takes by itself come first.
     */
    val boundKeys: List<RemoteKey>
        get() {
            val usual = KeyBindingPolicy.defaultBindings(voiceAvailable = false).keys.map { it.first }
            return (usual + settings.bindings.map { it.key }).distinct().filter {
                it != RemoteKey.Home &&
                    it != RemoteKey.Mic
            }
        }
}

@HiltViewModel
class ButtonsViewModel @Inject constructor(
    private val settings: RemoteSettingsRepository,
    installed: InstalledApps,
    otherHome: OtherHome,
    screens: DeviceScreens,
    search: Search,
    profile: DeviceProfile,
    service: AccessibilityHealth,
    private val capture: KeyCapture,
) : ViewModel() {

    /** Ticks each time the page is back on screen: the service may have been turned on or off meanwhile. */
    private val shown = MutableStateFlow(0)

    val state: StateFlow<ButtonsState?> = combine(
        settings.settings,
        installed.observe(),
        otherHome.choice,
        search.choice,
        shown.map { screens.hasSettings() to service.isServiceBound() }.flowOn(Dispatchers.IO),
    ) { remote, apps, home, searched, (deviceSettings, serviceOn) ->
        ButtonsState(
            settings = remote,
            apps = apps.sortedBy { it.label.lowercase() },
            otherHome = home.chosen,
            deviceSettings = deviceSettings,
            search = searched != null,
            mic = profile.hasRemoteMic,
            serviceOn = serviceOn,
            volumeHoldable = profile.heldVolumeStops,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /** The page is on screen again: what the device has is read anew. */
    fun recheck() {
        shown.value++
    }

    /** The buttons pressed while this is collected, as the remote's service sees them. */
    fun pressed(): Flow<RemoteKey> = capture.keys()

    /** The gestures made on [key] while this is collected; the button does nothing else meanwhile. */
    fun tried(key: RemoteKey): Flow<KeyGesture> = capture.gestures(key)

    /** Null [action]: as on the device. */
    fun bind(key: RemoteKey, gesture: KeyGesture, action: RemoteAction?) {
        viewModelScope.launch { settings.update { it.withBinding(key, gesture, action) } }
    }

    /** [only]: the button is Lumbina's on its own screens alone. */
    fun limit(key: RemoteKey, only: Boolean) {
        viewModelScope.launch { settings.update { it.withLumbinaOnly(key, only) } }
    }

    /** A blank [name] gives the button back the name its code has. */
    fun rename(key: RemoteKey, name: String) {
        viewModelScope.launch { settings.update { it.withKeyName(key, name) } }
    }

    fun toggleVolumeHold() {
        viewModelScope.launch { settings.update { it.copy(volumeHold = !it.volumeHold) } }
    }

    /** Nothing of the user's on [key] any more; with no key, on any. */
    fun free(key: RemoteKey? = null) {
        viewModelScope.launch { settings.update { it.withoutBindings(key) } }
    }
}
