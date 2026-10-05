package dev.lumbina.tv.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.common.OUR_ACCESSIBILITY_SERVICE
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.ComputerSetup
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.SetupResult
import dev.lumbina.tv.core.domain.access.SetupScreens
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import dev.lumbina.tv.core.domain.health.SecureSettings
import dev.lumbina.tv.core.domain.phone.LocalAddress
import dev.lumbina.tv.core.domain.speech.ModelState
import dev.lumbina.tv.core.domain.speech.VoiceLanguages
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import dev.lumbina.tv.core.ui.serviceScope
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Models of the enabled languages, in order of preference, and what is left to download. */
data class VoiceSetup(val models: Map<String, ModelState>, val missingBytes: Long) {
    val ready: Boolean get() = models.isNotEmpty() && models.values.all { it == ModelState.Installed }
    val busy: Boolean get() = models.values.any { it is ModelState.Downloading || it == ModelState.Unpacking }
}

/** How the remote's service gets turned on on this device. */
enum class ServicePath {
    /** Lumbina connects to the device's debugging itself: turn debugging on, press Connect. */
    LocalAdb,

    /** The system's accessibility settings. */
    AccessibilitySettings,

    /** Neither: from a computer. */
    Computer,
}

/** The commands for a computer; without the device's address they hold a placeholder the user fills in. */
data class ComputerCommands(val lines: List<String>, val addressKnown: Boolean)

/**
 * Where pressing "Connect" got; null before the first press, and again if the service doesn't
 * come on within a while of [TurningOn].
 */
enum class ConnectResult {
    Connecting,
    NotAllowed,
    DebuggingOff,

    /** Connected, but the device didn't let Lumbina keep its service on. */
    NotKept,

    /** Connected and set up: the service is coming on, until the next poll sees it (or gives up). */
    TurningOn,

    /** Connecting failed on Lumbina's side (its key); the log says why. */
    Failed,
}

/**
 * What still needs to be done: the service (when it isn't running) and speech models ([voice]
 * is null on devices without a remote mic Lumbina can use). [adb] is known on the [ServicePath.LocalAdb] path.
 */
data class SetupStatus(
    val serviceRunning: Boolean,
    val voice: VoiceSetup? = null,
    val path: ServicePath = ServicePath.Computer,
    val adb: AdbState? = null,
    val connect: ConnectResult? = null,
    /** The service is down but Lumbina is bringing it back itself: "starting", not steps. */
    val starting: Boolean = false,
) {
    val steps: Int get() = (if (serviceRunning) 0 else 1) + (if (voice?.ready == false) 1 else 0)

    /** Of [steps], those the user has to take: not the service while Lumbina is starting it. */
    val userSteps: Int get() = if (starting && !serviceRunning) steps - 1 else steps
    val complete: Boolean get() = steps == 0

    /** Debugging is known to be on, so "Connect" can work. */
    val debuggingOn: Boolean get() = adb != null && adb != AdbState.Off
}

@Suppress("LongParameterList") // one port per setup step
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val health: AccessibilityHealth,
    private val secureSettings: SecureSettings,
    private val voiceLanguages: VoiceLanguages,
    private val access: LocalAccess,
    private val screens: SetupScreens,
    private val accessSettings: AccessSettingsRepository,
    private val address: LocalAddress,
    @Named(OUR_ACCESSIBILITY_SERVICE) private val ourService: String,
    profile: DeviceProfile,
) : ViewModel() {

    /** What the consent says the service does here. */
    val serviceScope = profile.serviceScope()

    private val path = when {
        profile.localAdb -> ServicePath.LocalAdb
        screens.hasAccessibilitySettings -> ServicePath.AccessibilitySettings
        else -> ServicePath.Computer
    }

    init {
        // What a newer Lumbina sets up over its connection reaches those who connected before.
        if (path == ServicePath.LocalAdb) viewModelScope.launch(Dispatchers.IO) { access.renew() }
    }

    /**
     * Polled while visible: the service and debugging change from outside, with no callback.
     * Often while the service is missing, rarely once it runs.
     */
    private val service = flow {
        while (true) {
            val running = health.isServiceBound()
            emit(running to if (!running && path == ServicePath.LocalAdb) access.state() else null)
            delay(if (running) IDLE_POLL_MILLIS else POLL_MILLIS)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    private val voice = if (!profile.hasRemoteMic) {
        flowOf(null)
    } else {
        voiceLanguages.state.map { state ->
            VoiceSetup(state.enabled.associate { it.language to it.model }, state.missingBytes)
        }
    }

    private val connect = MutableStateFlow<ConnectResult?>(null)

    /** The first seconds after the screen opens, while the app brings the service back (see `healOnUiOpen`). */
    private val justOpened = flow {
        emit(true)
        delay(STARTING_MILLIS)
        emit(false)
    }

    /** Null while loading and once the user chose "Hide". */
    val status: StateFlow<SetupStatus?> = combine(
        service,
        voice,
        connect,
        accessSettings.settings.map { it.setupCardHidden },
        justOpened,
    ) { (running, adb), voice, connect, hidden, justOpened ->
        val starting = justOpened && !running && secureSettings.canWrite
        SetupStatus(running, voice, path, adb, connect, starting).takeUnless { hidden }
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun downloadSpeechModels() {
        viewModelScope.launch { voiceLanguages.installEnabled() }
    }

    fun hide() {
        viewModelScope.launch { accessSettings.update { it.copy(setupCardHidden = true) } }
    }

    fun openDeveloperSettings() {
        screens.openDeveloperSettings()
    }

    fun openAccessibilitySettings() {
        screens.openAccessibilitySettings()
    }

    /** Connects to debugging (the device may ask the user) and lets Lumbina keep its service on. */
    fun connect() {
        if (connect.value == ConnectResult.Connecting) return
        connect.value = ConnectResult.Connecting
        viewModelScope.launch {
            connect.value = when (access.connectAndSetUp()) {
                SetupResult.Done -> ConnectResult.TurningOn
                SetupResult.NotKept -> ConnectResult.NotKept
                SetupResult.NotAllowed -> ConnectResult.NotAllowed
                SetupResult.Off -> ConnectResult.DebuggingOff
                SetupResult.Failed -> ConnectResult.Failed
            }
            if (connect.value == ConnectResult.TurningOn) {
                // The card goes once a poll sees the service; if it doesn't come, back to the plain hint.
                delay(TURN_ON_MILLIS)
                connect.compareAndSet(ConnectResult.TurningOn, null)
            }
        }
    }

    /** The commands for a computer, with this device's address and current services filled in. */
    suspend fun computerCommands(packageName: String): ComputerCommands = withContext(Dispatchers.IO) {
        val ip = address.ipv4()
        ComputerCommands(
            ComputerSetup.commands(ip, secureSettings.enabledAccessibilityServices(), ourService, packageName),
            addressKnown = ip != null,
        )
    }

    private companion object {
        const val POLL_MILLIS = 3_000L
        const val TURN_ON_MILLIS = 15_000L
        const val STARTING_MILLIS = 20_000L
        const val IDLE_POLL_MILLIS = 60_000L
    }
}
