package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.access.AccessSettings
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.SetupResult
import dev.lumbina.tv.core.domain.access.SetupScreens
import dev.lumbina.tv.core.domain.access.UsageAccess
import dev.lumbina.tv.core.domain.access.withMicApp
import dev.lumbina.tv.core.domain.access.withoutMicApp
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import dev.lumbina.tv.core.ui.serviceScope
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the device lets Lumbina do right now ([adb] null while checking), and what Lumbina is asked to do with it. */
data class AccessState(
    val adb: AdbState?,
    val keepsService: Boolean,
    val usageGranted: Boolean,
    val settings: AccessSettings,
    val connecting: Boolean = false,
    /** What the last press of "Connect" came to, while it has not connected: said, or the press seems lost. */
    val tried: SetupResult? = null,
    /** The apps asked about so far; null where the device needs no such thing. */
    val micApps: List<MicApp>? = null,
)

/** An app that records the microphone itself, and whether the user lets it hear the remote. */
data class MicApp(val packageName: String, val label: String, val allowed: Boolean)

/** What the Access page's rows do. */
interface AccessCommands {
    fun openDeveloperSettings()

    fun connect()

    fun toggleKeepService()

    fun grantUsageStats()

    fun toggleCloseFromRecents()

    fun toggleMicApp(app: MicApp)

    fun forgetMicApp(app: MicApp)
}

@HiltViewModel
class AccessViewModel @Inject constructor(
    private val access: LocalAccess,
    private val settings: AccessSettingsRepository,
    private val screens: SetupScreens,
    private val usage: UsageAccess,
    installed: InstalledApps,
    profile: DeviceProfile,
) : ViewModel(),
    AccessCommands {

    /** What the consent says the service does here. */
    val serviceScope = profile.serviceScope()

    private val refresh = Channel<Unit>(Channel.CONFLATED)
    private val connecting = MutableStateFlow(false)
    private val tried = MutableStateFlow<SetupResult?>(null)

    /** Debugging and grants change from outside, with no callback: re-read now and then or after a change. */
    private val device = flow {
        emit(Device(null, access.keepsService, usage.granted()))
        while (true) {
            emit(Device(access.state(), access.keepsService, usage.granted()))
            withTimeoutOrNull(REFRESH_MILLIS) { refresh.receive() }
        }
    }.flowOn(Dispatchers.IO)

    private val micInjection = profile.feedsMicToApps

    /** Names for [AccessSettings.micApps]: an app gone from the device keeps its package name. */
    private val labels = installed.observe().map { apps -> apps.associate { it.id.packageName to it.label } }

    val state: StateFlow<AccessState?> =
        combine(device, settings.settings, connecting, labels, tried) { device, set, busy, names, tried ->
            val micApps = set.micApps
                .map { (app, allowed) -> MicApp(app, names[app] ?: app, allowed) }
                .sortedBy { it.label }
                .takeIf { micInjection }
            AccessState(device.adb, device.keepsService, device.usageGranted, set, busy, tried, micApps)
        }.stateIn(viewModelScope, WhileUiSubscribed, null)

    override fun openDeveloperSettings() {
        screens.openDeveloperSettings()
    }

    override fun connect() {
        if (connecting.value) return
        connecting.value = true
        tried.value = null
        viewModelScope.launch {
            try {
                tried.value = access.connectAndSetUp().takeUnless { it == SetupResult.Done }
            } finally {
                connecting.value = false
                refresh.trySend(Unit)
            }
        }
    }

    override fun toggleKeepService() {
        val on = state.value?.keepsService ?: return
        viewModelScope.launch {
            access.setKeepService(!on)
            refresh.trySend(Unit)
        }
    }

    override fun grantUsageStats() {
        viewModelScope.launch {
            access.grantUsageStats()
            refresh.trySend(Unit)
        }
    }

    override fun toggleCloseFromRecents() {
        viewModelScope.launch { settings.update { it.copy(closeFromRecents = !it.closeFromRecents) } }
    }

    override fun toggleMicApp(app: MicApp) {
        viewModelScope.launch { settings.update { it.withMicApp(app.packageName, !app.allowed) } }
    }

    override fun forgetMicApp(app: MicApp) {
        viewModelScope.launch { settings.update { it.withoutMicApp(app.packageName) } }
    }

    private data class Device(val adb: AdbState?, val keepsService: Boolean, val usageGranted: Boolean)

    private companion object {
        const val REFRESH_MILLIS = 10_000L
    }
}
