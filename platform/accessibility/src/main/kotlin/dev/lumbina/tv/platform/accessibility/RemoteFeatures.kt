package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.device.FirmwarePart
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.domain.remote.KeyAction
import dev.lumbina.tv.core.domain.remote.KeyBinding
import dev.lumbina.tv.core.domain.remote.KeyBindingPolicy
import dev.lumbina.tv.core.domain.remote.LumbinaHome
import dev.lumbina.tv.core.domain.remote.RawKey
import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.remote.RemoteKey
import dev.lumbina.tv.core.domain.remote.RemoteKeyInterpreter
import dev.lumbina.tv.core.domain.remote.RemoteSettingsRepository
import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.voice.DeviceControls
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Everything the service does for one connection, built by [Factory]: starts and stops the
 * [Parts] in order, offers them each remote key before the [RemoteKeyInterpreter] turns it into
 * a [RemoteAction], and carries those out. Main thread.
 */
internal class RemoteFeatures(
    private val parts: Parts,
    private val profile: DeviceProfile,
    private val lumbinaHome: LumbinaHome,
    private val remoteSettings: RemoteSettingsRepository,
    private val scope: CoroutineScope,
    private val log: LumbinaLog,
) {
    class Parts(
        val voice: RemoteVoice,
        val micKey: MicKey,
        val forApps: ForApps,
        val switching: AppSwitching,
        val powerOn: PowerOnWatcher,
        val quickMenu: QuickMenu,
        val sleep: SleepWatcher,
        val phoneRemote: PhoneRemote,
        /** What buttons the user set do. */
        val bound: BoundActions,
        /** Tells the settings which button was pressed, while they ask. */
        val capture: KeyCapturing,
        /** Whether one of Lumbina's own screens is in front. */
        val inLumbina: () -> Boolean,
        /** Only in debuggable builds. */
        val debug: DebugCommands?,
        /** Where a held volume button stops repeating ([DeviceProfile.heldVolumeStops]). */
        val volumeHold: VolumeHold?,
    )

    /**
     * What the device's profile asks for besides voice input, over Lumbina's own adb: Lumbina as
     * the system's speech recognizer, and the remote's mic ([mic]) for apps that record by themselves,
     * each with the user's leave ([micPrompt]).
     */
    class ForApps(
        val recognizerSetup: RecognizerSetup?,
        val micInjection: MicInjection?,
        val micPrompt: MicAppPrompt?,
        val mic: AudioSource?,
        /** Takes back what was handed to those who outlive the service. */
        val detach: () -> Unit = {},
    )

    private val interpreter = RemoteKeyInterpreter(
        keyMap = profile.keyMap,
        policy = KeyBindingPolicy.defaults(voiceAvailable = false),
        scope = scope,
        onAction = ::perform,
        now = SystemClock::uptimeMillis,
        inLumbina = parts.inLumbina,
        // Only on Lumbina's screens: settings that went away without saying so must not keep a button dead.
        probe = { key, gesture -> parts.capture.probe(key, gesture, parts.inLumbina) },
    )
    private var started = false

    /** What the keys are bound by: whether voice input is there to take the mic key, and the user's settings. */
    private var voiceAvailable = false
    private var ownBindings: List<KeyBinding> = emptyList()
    private var lumbinaOnly: Set<RemoteKey> = KeyBindingPolicy.DEFAULT_LUMBINA_ONLY

    /** When each key was last written to the log: what this remote sends is logged again only after a while. */
    private val loggedKeys = HashMap<Long, Long>()

    /** Once only: the system may connect the same service instance again. */
    fun start() {
        log.log(TAG, "connected, device profile: ${profile.id}")
        if (started) return
        started = true
        with(parts) {
            voice.start(scope) {
                voiceAvailable = it
                bindKeys()
            }
            scope.launch {
                remoteSettings.settings.collect {
                    ownBindings = it.bindings
                    lumbinaOnly = it.limited
                    volumeHold?.enabled = it.volumeHold
                    bindKeys()
                }
            }
            start(forApps)
            switching.start(scope)
            powerOn.start(scope)
            quickMenu.start(scope)
            sleep.start(scope)
            phoneRemote.start(scope)
            volumeHold?.busy = ::ownScreenShows
            volumeHold?.start()
            capture.start()
            debug?.key = { code, what ->
                onKey(KeyEvent(if (what == "up") KeyEvent.ACTION_UP else KeyEvent.ACTION_DOWN, code))
            }
            debug?.register()
        }
    }

    private fun bindKeys() {
        interpreter.policy = KeyBindingPolicy.defaults(voiceAvailable, ownBindings, lumbinaOnly)
    }

    fun stop() {
        if (!started) return
        started = false
        with(parts) {
            phoneRemote.stop()
            volumeHold?.stop()
            capture.stop()
            debug?.unregister()
            switching.dispose()
            quickMenu.hide()
            sleep.stop()
            forApps.micInjection?.stop()
            forApps.detach()
            forApps.micPrompt?.hide()
            voice.stop()
            powerOn.stop()
        }
    }

    private fun start(forApps: ForApps) {
        val mic = forApps.mic ?: return
        forApps.recognizerSetup?.let { setup -> scope.launch(Dispatchers.IO) { setup.run() } }
        forApps.micInjection?.start(scope, mic)
    }

    fun onKey(event: KeyEvent): Boolean {
        note(event)
        return offer(event)
    }

    /** Overlays that are showing take keys first (the sleep warning above all), then the remote's bindings. */
    private fun offer(event: KeyEvent): Boolean {
        if (parts.sleep.onKey(event) || parts.forApps.micPrompt?.onKey(event) == true) return true
        if (parts.capture.onKey(event, parts.inLumbina)) return true
        if (parts.quickMenu.onKey(event) || parts.switching.onKey(event)) return true
        if (parts.voice.onKey(event) || parts.debug?.onKey(event) == true) return true
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> KeyAction.Down
            KeyEvent.ACTION_UP -> KeyAction.Up
            else -> return false
        }
        return interpreter.onKey(RawKey(event.scanCode, event.keyCode, action, repeat = event.repeatCount > 0))
    }

    /** Whether a screen of the service's own shows over the app: it has the remote's keys then. */
    private fun ownScreenShows(): Boolean = with(parts) {
        sleep.warns || quickMenu.isShowing || switching.isShowing || forApps.micPrompt?.takesKeys == true ||
            capture.isAsking
    }

    /** Printing keys are skipped: what is typed stays out of the log. */
    private fun note(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return
        val key = event.scanCode.toLong() shl Int.SIZE_BITS or event.keyCode.toLong()
        val now = SystemClock.uptimeMillis()
        val logged = loggedKeys[key]
        if ((logged != null && now - logged < KEY_LOG_MILLIS) || event.isPrintingKey) return
        loggedKeys[key] = now
        val from = event.device?.let { String.format(Locale.US, "%s (%04x:%04x)", it.name, it.vendorId, it.productId) }
        val ours = profile.keyMap.map(event.scanCode, event.keyCode)
        log.log(TAG, "key: scan ${event.scanCode}, code ${event.keyCode}, ${ours ?: "not ours"}, from $from")
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        parts.switching.onAccessibilityEvent(event)
        parts.volumeHold?.onAccessibilityEvent(event)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            parts.powerOn.onWindow(event.packageName, event.className)
            parts.forApps.micPrompt?.onWindowChanged()
        }
        parts.sleep.onWindowChanged()
    }

    private fun perform(action: RemoteAction) {
        if (action != RemoteAction.StopVoice && action != RemoteAction.CancelVoice) log.log(TAG, "action: $action")
        when (action) {
            RemoteAction.OpenLumbina -> lumbinaHome.open(toStart = false)

            RemoteAction.ShowQuickMenu -> {
                parts.switching.hide()
                parts.quickMenu.show()
            }

            RemoteAction.StartVoice -> parts.micKey.pressed()

            RemoteAction.StopVoice -> parts.micKey.released()

            RemoteAction.CancelVoice -> parts.micKey.tapped()

            RemoteAction.CycleVoiceLanguage -> parts.micKey.doubleTapped()

            RemoteAction.ShowAppSwitcher -> parts.switching.toggle()

            RemoteAction.OpenPreviousApp -> parts.switching.previous()

            else -> parts.bound.perform(action)
        }
    }

    /** Builds the features for a connected [AccessibilityService]; the parts share its [CoroutineScope]. */
    class Factory @Inject constructor(
        private val device: RemoteDeps.Device,
        private val apps: RemoteDeps.Apps,
        private val voice: RemoteDeps.Voice,
        private val stores: RemoteDeps.Stores,
    ) {
        /** OK held from the phone's page: by Lumbina's own access to the device, where the profile has one. */
        private fun holdOk(): (suspend () -> Boolean)? = if (device.profile.localAdb) {
            { apps.localAccess.holdKey(KeyEvent.KEYCODE_DPAD_CENTER) }
        } else {
            null
        }

        private fun keyOf(profile: DeviceProfile, event: KeyEvent): RemoteKey =
            profile.keyMap.map(event.scanCode, event.keyCode) ?: RemoteKey.of(event.scanCode, event.keyCode)

        private fun quickMenuPorts(otherHome: Flow<HomeScreen?>, switching: AppSwitching, say: (String) -> Unit) =
            QuickMenu.Ports(
                otherHome = otherHome,
                launcher = apps.launcher,
                recentApps = switching::show,
                phone = stores.phoneRemote.settings,
                address = device.address,
                screens = device.screens,
                say = say,
            )

        private fun switching(
            service: AccessibilityService,
            launchers: Set<String>,
            say: (String) -> Unit,
        ): AppSwitching {
            val closing = if (device.profile.localAdb) {
                AppSwitching.Closing(apps.accessSettings, apps.localAccess)
            } else {
                null
            }
            val ports = AppSwitching.Ports(say, device.lumbinaHome)
            return AppSwitching(
                service,
                apps.installed,
                apps.launcher,
                launchers,
                apps.recent,
                closing,
                ports,
                device.log,
            )
        }

        fun create(service: AccessibilityService, scope: CoroutineScope): RemoteFeatures {
            val profile = device.profile
            val log = device.log
            val own = setOfNotNull(service.packageName, profile.stockLauncher?.packageName)
            // Read off the main thread too; the other home screen in use joins it as the user chooses.
            val launchers = ConcurrentHashMap.newKeySet<String>().apply { addAll(own) }
            val volumeHold = if (profile.heldVolumeStops) VolumeHold(service, scope, log, service::filterKeys) else null
            val controls = controls(service, launchers, volumeHold)
            val assembly = voiceAssembly(service, launchers, commandPorts(controls))
            val sleep = SleepWatcher(service, stores.sleep, controls::standby, log)
            followTheme(service, stores.appearance.settings, scope)
            // What an overlay could not open or close is said on the voice's plate.
            val say = { text: String -> assembly.plate.flash(text, NOTE_MILLIS) }
            val switching = switching(service, launchers, say)
            var homeNow: HomeScreen? = null
            val otherHome = stores.otherHome.choice.map { it.chosen }.onEach { home ->
                homeNow = home
                home?.let { launchers += it.id.packageName }
                launchers.retainAll(own + listOfNotNull(home?.id?.packageName))
            }
            val quickMenuPorts = quickMenuPorts(otherHome, switching, say)
            val quickMenu = QuickMenu(service, stores.sleep, quickMenuPorts)
            val phonePorts = PhoneRemote.Ports(
                controls = controls,
                deliver = assembly::textDelivery,
                folder = stores.photoFolder,
                wallpaper = stores.wallpaper,
                speechKeys = voice.keys,
                youTube = voice.youTube,
                quickMenu = {
                    switching.hide()
                    quickMenu.show()
                },
                appSwitcher = switching::show,
                overlayKey = { event -> quickMenu.onKey(event) || switching.onKey(event) },
                holdOk = holdOk(),
                hasMic = profile.hasRemoteMic,
            )
            // One mic connection for everything: the remote has room for one reader only.
            val mic = profile.remoteMicAudio?.let { assembly.warm(scope, it) }
            val readiness = VoiceReadiness(voice.engine, log)
            val remoteVoice = RemoteVoice(service, assembly, voice.setups, readiness, mic, log)
            val forApps = forApps(service, scope, mic, controls::appInFront, sleep)
            val micKey = MicKey(remoteVoice, forApps.micInjection)
            val debug = debugCommands(service, scope, assembly, readiness, switching, quickMenu, sleep)
            debug?.mic = micKey::debug
            val parts = Parts(
                voice = remoteVoice,
                micKey = micKey,
                forApps = forApps,
                switching = switching,
                powerOn = powerOn(service),
                quickMenu = quickMenu,
                sleep = sleep,
                phoneRemote = PhoneRemote(service, stores.phoneRemote, phonePorts, log),
                bound = BoundActions(service, apps.launcher, controls, device.screens, { homeNow }, say),
                capture = KeyCapturing(service, { keyOf(profile, it) }, log),
                inLumbina = { service.rootInActiveWindow?.packageName == service.packageName },
                debug = debug,
                volumeHold = volumeHold,
            )
            return RemoteFeatures(parts, profile, device.lumbinaHome, stores.remote, scope, log)
        }

        /** Apps are stopped over Lumbina's own adb where it has one; its volume changes are told to [volumeHold]. */
        private fun controls(service: AccessibilityService, launchers: Set<String>, volumeHold: VolumeHold?) =
            ServiceDeviceControls(
                service,
                device.lumbinaHome,
                device.profile.volume,
                launchers,
                stop = if (device.profile.localAdb) apps.localAccess::forceStop else null,
                ownVolume = { volumeHold?.ownChange() },
            )

        private fun voiceAssembly(
            service: AccessibilityService,
            launchers: Set<String>,
            commandPorts: VoiceAssembly.CommandPorts,
        ) = VoiceAssembly(
            service,
            voice.engine,
            voice.settings,
            VoiceAssembly.Searches(voice.youTubeSearch, voice.search),
            launchers,
            commandPorts,
            device.log,
            micServiceOff = { device.firmware.isOff(FirmwarePart.Feature.RemoteMic) },
        )

        private fun powerOn(service: AccessibilityService) = PowerOnWatcher(
            service,
            apps.accessSettings,
            // Without a launcher of the firmware there is no home screen of its to open over.
            isHomeScreen = device.profile.takeIf { it.stockLauncher != null }?.let { it::isHomeScreen },
            frontPackage = { service.rootInActiveWindow?.packageName?.toString() },
            open = { device.lumbinaHome.open(toStart = true) },
            log = device.log,
        )

        private fun commandPorts(controls: DeviceControls) = VoiceAssembly.CommandPorts(
            apps.installed,
            voice.layout,
            apps.launcher,
            controls,
            stores.sleep,
            voice.youTube,
        )

        private fun forApps(
            service: AccessibilityService,
            scope: CoroutineScope,
            mic: AudioSource?,
            inFront: () -> String?,
            sleep: SleepWatcher,
        ): ForApps {
            val profile = device.profile
            val log = device.log
            val adb = profile.localAdb
            val recognizerSetup =
                if (adb && profile.speechForApps) RecognizerSetup(service, apps.localAccess, log) else null
            val asks = profile.feedsMicToApps
            val prompt = if (asks) {
                val texts = MicAppPrompt.texts(service)
                // Over the warning before the device turns off, which has the keys while both show.
                MicAppPrompt(CornerCard(service, under = sleep.card), texts, apps.accessSettings, inFront, scope, log)
                    .apply {
                        paused = { sleep.warns }
                        openAccess = { service.openLumbina(LumbinaActions.SETTINGS_PAGE_ACCESS) }
                    }
            } else {
                null
            }
            val micInjection =
                prompt?.let { MicInjection(service, apps.localAccess, apps.accessSettings, it::ask, log) }
            micInjection?.let { injection -> prompt.started = { injection.outcomes.first() } }
            val speech = micInjection?.speech
            voice.appSpeech.feed = speech
            val detach = { if (voice.appSpeech.feed === speech) voice.appSpeech.feed = null }
            return ForApps(recognizerSetup, micInjection, prompt, mic, detach)
        }

        /** Only in debuggable builds. */
        @Suppress("LongParameterList")
        private fun debugCommands(
            service: AccessibilityService,
            scope: CoroutineScope,
            assembly: VoiceAssembly,
            readiness: VoiceReadiness,
            switching: AppSwitching,
            quickMenu: QuickMenu,
            sleep: SleepWatcher,
        ): DebugCommands? {
            if (service.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
            return DebugCommands(
                service = service,
                scope = scope,
                deliver = assembly::textDelivery,
                say = assembly::utterances,
                answers = assembly.answers(scope),
                hear = DebugHearing(voice.setups, readiness, assembly.sessions)::hear,
                switcher = switching::debug,
                quickMenu = quickMenu::debug,
                sleepIn = sleep::debugSleepIn,
                log = device.log,
            )
        }
    }

    private companion object {
        const val TAG = "a11y"

        /** A key is logged again after this long: a report carries only the log's tail. */
        const val KEY_LOG_MILLIS = 10 * 60_000L

        /** How long a note about what did not open stays at the bottom of the screen. */
        const val NOTE_MILLIS = 4_000L
    }
}
