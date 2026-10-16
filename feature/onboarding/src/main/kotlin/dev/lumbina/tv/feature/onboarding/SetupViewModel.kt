package dev.lumbina.tv.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.common.OUR_ACCESSIBILITY_SERVICE
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.ComputerSetup
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.NotificationAccess
import dev.lumbina.tv.core.domain.access.SetupResult
import dev.lumbina.tv.core.domain.access.SetupScreens
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import dev.lumbina.tv.core.domain.health.SecureSettings
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.domain.phone.LocalAddress
import dev.lumbina.tv.core.domain.speech.ModelState
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import dev.lumbina.tv.core.domain.speech.VoiceLanguages
import dev.lumbina.tv.core.domain.speech.VoiceSettingsRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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

    /** Allowed once, without "Always allow": nothing could be set up over it. */
    AllowedOnce,

    /** Connected and set up: the service is coming on, until the next poll sees it (or gives up). */
    TurningOn,

    /** Connecting failed on Lumbina's side (its key); the log says why. */
    Failed,
}

/**
 * Where the setup stands: the service and how it is turned on ([path]; [adb] is known on the
 * [ServicePath.LocalAdb] path, [connect] after a try), and the speech models ([voice]: null where
 * there is no remote mic Lumbina can use, or voice is not recognized on the device).
 */
data class SetupStatus(
    val serviceRunning: Boolean,
    val voice: VoiceSetup? = null,
    val path: ServicePath = ServicePath.Computer,
    val adb: AdbState? = null,
    val connect: ConnectResult? = null,
    /** The service is down but Lumbina is bringing it back itself: "starting", not one of [userSteps]. */
    val starting: Boolean = false,
    /** The permission to read apps' channels, where the user has yet to give it; null: nothing to ask. */
    val channels: String? = null,
    /** Notification access is the user's to give here, and not given yet. */
    val noticesMissing: Boolean = false,
    /** The setup was asked back after it was over: it opens at its steps, with no welcome. */
    val again: Boolean = false,
) {
    /** How many of the setup's steps are not done. */
    val steps: Int
        get() = listOf(!serviceRunning, channels != null, noticesMissing, voice?.ready == false).count { it }
    val complete: Boolean get() = steps == 0

    /** The steps the user has to take: the service is not one while Lumbina brings it back itself. */
    val userSteps: Int get() = if (starting && !serviceRunning) steps - 1 else steps

    /** Debugging is known to be on, so "Connect" can work. */
    val debuggingOn: Boolean get() = adb != null && adb != AdbState.Off
}

/** What a poll of the device found: none of it has a callback. */
private data class Polled(
    val running: Boolean,
    val adb: AdbState?,
    val channels: String?,
    val noticesMissing: Boolean,
) {
    /** Nothing left that could change under the user's hands: polling can slow down. */
    val settled: Boolean get() = running && channels == null && !noticesMissing
}

@Suppress("LongParameterList") // one port per setup step
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val health: AccessibilityHealth,
    private val secureSettings: SecureSettings,
    private val voiceLanguages: VoiceLanguages,
    voiceSettings: VoiceSettingsRepository,
    private val access: LocalAccess,
    private val screens: SetupScreens,
    private val accessSettings: AccessSettingsRepository,
    private val address: LocalAddress,
    @Named(OUR_ACCESSIBILITY_SERVICE) private val ourService: String,
    private val appLanguage: AppLanguage,
    private val listings: TvListingsPermission,
    private val notices: NotificationAccess,
    profile: DeviceProfile,
) : ViewModel() {

    /** With its own debugging Lumbina gives itself notification access as it connects: no step for the user. */
    private val noticesByUser = !profile.localAdb

    /** Whether Lumbina's language can be chosen apart from the device's. */
    val languageSupported: Boolean = appLanguage.supported

    /** Whether to ask the language before anything else: see [DeviceProfile.languageFirst]. */
    val languageFirst: Boolean = profile.languageFirst && appLanguage.supported && appLanguage.current() == null

    val hasAccessibilitySettings: Boolean get() = screens.hasAccessibilitySettings

    fun language(): String? = appLanguage.current()

    /** Lumbina's language, and voice's with it: the setup offers to download that one. */
    fun setLanguage(tag: String?) {
        viewModelScope.launch {
            voiceLanguages.followAppLanguage(tag, previous = appLanguage.current())
            appLanguage.set(tag)
        }
    }

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
     * Often while the service is missing or the wizard waits for an answer, rarely otherwise.
     */
    private val service = flow {
        while (true) {
            val running = health.isServiceBound()
            val adb = if (!running && path == ServicePath.LocalAdb) access.state() else null
            val polled = Polled(running, adb, listings.missing(), noticesByUser && !notices.granted())
            emit(polled)
            // Steps put off with the wizard closed are nobody's to watch.
            val watched = !polled.settled && !accessSettings.settings.first().setupCardHidden
            delay(if (watched || !running) POLL_MILLIS else IDLE_POLL_MILLIS)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged().shareIn(viewModelScope, WhileUiSubscribed, replay = 1)

    private val voice = if (!profile.hasRemoteMic) {
        flowOf(null)
    } else {
        // Models are a step only for the device's own recognizer: a service or another app needs none.
        combine(voiceLanguages.state, voiceSettings.settings) { state, settings ->
            VoiceSetup(state.enabled.associate { it.language to it.model }, state.missingBytes)
                .takeIf { settings.provider == SpeechProvider.Device }
        }
    }

    private val connect = MutableStateFlow<ConnectResult?>(null)

    /** The first seconds after the screen opens, while the app brings the service back (see `healOnUiOpen`). */
    private val justOpened = flow {
        emit(true)
        delay(STARTING_MILLIS)
        emit(false)
    }

    /**
     * The setup is not over yet: its window is to come, so home stays covered until it does. Taken
     * so until the settings are read: the first launch would show home for a moment before.
     */
    val pending: StateFlow<Boolean> =
        accessSettings.settings.map { !it.setupCardHidden }.stateIn(viewModelScope, WhileUiSubscribed, true)

    /** Null while loading and once the setup was finished or skipped. */
    val status: StateFlow<SetupStatus?> = combine(
        service,
        voice,
        connect,
        accessSettings.settings.map { it.setupCardHidden to it.setupReopened },
        justOpened,
    ) { polled, voice, connect, (hidden, again), justOpened ->
        val starting = justOpened && !polled.running && secureSettings.canWrite
        SetupStatus(
            serviceRunning = polled.running,
            voice = voice,
            path = path,
            adb = polled.adb,
            connect = connect,
            starting = starting,
            channels = polled.channels,
            noticesMissing = polled.noticesMissing,
            again = again,
        ).takeUnless { hidden }
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /** The user answered the system's question about apps' channels. */
    fun onChannels(granted: Boolean) {
        if (granted) listings.onGranted()
    }

    fun openNotificationAccess(): Boolean = notices.openSettings()

    /** Downloads the models the voice step shows. */
    fun downloadSpeechModels() {
        status.value?.voice?.models?.keys?.let(voiceLanguages::install)
    }

    /** After the setup: the service is not running and Lumbina is not bringing it back, so home should say so. */
    val serviceOff: StateFlow<Boolean> = combine(
        service,
        accessSettings.settings.map { it.setupCardHidden },
        justOpened,
    ) { polled, dismissed, justOpened ->
        dismissed && !polled.running && !(justOpened && secureSettings.canWrite)
    }.stateIn(viewModelScope, WhileUiSubscribed, false)

    /** How many of the setup's steps are not done, whether or not the setup is on screen. */
    val left: StateFlow<Int> = combine(service, voice, justOpened) { polled, voice, justOpened ->
        SetupStatus(
            serviceRunning = polled.running,
            voice = voice,
            starting = justOpened && !polled.running && secureSettings.canWrite,
            channels = polled.channels,
            noticesMissing = polled.noticesMissing,
        ).userSteps
    }.stateIn(viewModelScope, WhileUiSubscribed, 0)

    /** The setup is over: finished, or skipped with its steps left for later. */
    fun finish() {
        viewModelScope.launch { accessSettings.update { it.copy(setupCardHidden = true, setupReopened = false) } }
    }

    /** Brings the setup back, at its steps. */
    fun reopen() {
        viewModelScope.launch { accessSettings.update { it.copy(setupCardHidden = false, setupReopened = true) } }
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
                SetupResult.AllowedOnce -> ConnectResult.AllowedOnce
                SetupResult.NotAllowed -> ConnectResult.NotAllowed
                SetupResult.Off -> ConnectResult.DebuggingOff
                SetupResult.Failed -> ConnectResult.Failed
            }
            if (connect.value == ConnectResult.TurningOn) {
                // The step is done once a poll sees the service; if it doesn't come, back to the plain hint.
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
