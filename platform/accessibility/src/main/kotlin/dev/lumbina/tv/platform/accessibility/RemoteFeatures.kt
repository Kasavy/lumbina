package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.remote.KeyAction
import dev.lumbina.tv.core.domain.remote.KeyBindingPolicy
import dev.lumbina.tv.core.domain.remote.LumbinaHome
import dev.lumbina.tv.core.domain.remote.RawKey
import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.remote.RemoteKeyInterpreter
import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.voice.DeviceControls
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        /** Only in debuggable builds. */
        val debug: DebugCommands?,
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
    )

    private val interpreter = RemoteKeyInterpreter(
        keyMap = profile.keyMap,
        policy = KeyBindingPolicy.defaults(voiceAvailable = false),
        scope = scope,
        onAction = ::perform,
        now = SystemClock::uptimeMillis,
    )
    private var started = false

    /** When each key was last written to the log: what this remote sends is logged again only after a while. */
    private val loggedKeys = HashMap<Long, Long>()

    /** Once only: the system may connect the same service instance again. */
    fun start() {
        log.log(TAG, "connected, device profile: ${profile.id}")
        if (started) return
        started = true
        with(parts) {
            voice.start(scope) { interpreter.policy = KeyBindingPolicy.defaults(voiceAvailable = it) }
            start(forApps)
            switching.start(scope)
            powerOn.start(scope)
            quickMenu.start(scope)
            sleep.start(scope)
            phoneRemote.start(scope)
            debug?.register()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        with(parts) {
            phoneRemote.stop()
            debug?.unregister()
            switching.dispose()
            quickMenu.hide()
            sleep.stop()
            forApps.micInjection?.stop()
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
        if (parts.quickMenu.onKey(event) || parts.switching.onKey(event)) return true
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> KeyAction.Down
            KeyEvent.ACTION_UP -> KeyAction.Up
            else -> return false
        }
        return interpreter.onKey(RawKey(event.scanCode, event.keyCode, action))
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

            RemoteAction.ShowAppSwitcher -> parts.switching.show()
        }
    }

    /** Builds the features for a connected [AccessibilityService]; the parts share its [CoroutineScope]. */
    class Factory @Inject constructor(
        private val device: RemoteDeps.Device,
        private val apps: RemoteDeps.Apps,
        private val voice: RemoteDeps.Voice,
        private val stores: RemoteDeps.Stores,
    ) {
        fun create(service: AccessibilityService, scope: CoroutineScope): RemoteFeatures {
            val profile = device.profile
            val lumbinaHome = device.lumbinaHome
            val log = device.log
            val launchers = setOfNotNull(service.packageName, profile.stockLauncher?.packageName)
            val stop = if (profile.localAdb) apps.localAccess::forceStop else null
            val controls = ServiceDeviceControls(service, lumbinaHome, profile.volume, launchers, stop)
            val commandPorts = commandPorts(controls)
            val assembly =
                VoiceAssembly(service, voice.engine, voice.settings, voice.youTubeSearch, launchers, commandPorts, log)
            val sleep = SleepWatcher(service, stores.sleep, controls::standby, log)
            val closing = if (profile.localAdb) AppSwitching.Closing(apps.accessSettings, apps.localAccess) else null
            val switching = AppSwitching(service, apps.installed, apps.launcher, launchers, apps.recent, closing, log)
            val quickMenuPorts = QuickMenu.Ports(profile.stockLauncher, apps.launcher, recentApps = switching::show)
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
            )
            val powerOn = PowerOnWatcher(
                service,
                apps.accessSettings,
                profile.stockLauncher?.packageName,
                frontPackage = { service.rootInActiveWindow?.packageName?.toString() },
                open = { lumbinaHome.open(toStart = true) },
                log = log,
            )
            // One mic connection for everything: the remote has room for one reader only.
            val mic = profile.remoteMicAudio?.let { assembly.warm(scope, it) }
            val readiness = VoiceReadiness(voice.engine, log)
            val remoteVoice = RemoteVoice(service, assembly, voice.setups, readiness, mic, log)
            val forApps = forApps(service, scope, mic, controls::appInFront)
            val parts = Parts(
                voice = remoteVoice,
                micKey = MicKey(remoteVoice, forApps.micInjection),
                forApps = forApps,
                switching = switching,
                powerOn = powerOn,
                quickMenu = quickMenu,
                sleep = sleep,
                phoneRemote = PhoneRemote(service, stores.phoneRemote, phonePorts, log),
                debug = debugCommands(service, scope, assembly, readiness, switching, quickMenu, sleep),
            )
            return RemoteFeatures(parts, profile, lumbinaHome, scope, log)
        }

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
        ): ForApps {
            val profile = device.profile
            val log = device.log
            val adb = profile.localAdb
            val recognizerSetup =
                if (adb && profile.speechForApps) RecognizerSetup(service, apps.localAccess, log) else null
            val asks = profile.feedsMicToApps
            val prompt = if (asks) {
                val texts = MicAppPrompt.texts(service)
                MicAppPrompt(CornerCard(service), texts, apps.accessSettings, inFront, scope, log)
            } else {
                null
            }
            val micInjection =
                prompt?.let { MicInjection(service, apps.localAccess, apps.accessSettings, it::ask, log) }
            return ForApps(recognizerSetup, micInjection, prompt, mic)
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
    }
}
