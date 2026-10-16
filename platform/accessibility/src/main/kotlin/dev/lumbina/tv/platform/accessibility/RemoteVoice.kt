package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.speech.SpeechSetups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Voice input on the remote's mic ([audio], null where the remote has none): follows
 * [SpeechSetups] and the launcher's model downloads, and builds the [controller] and [forApps]
 * once recognition can work ([VoiceReadiness]). The launcher's on-screen mic key toggles it.
 * Main thread.
 */
internal class RemoteVoice(
    private val service: AccessibilityService,
    private val assembly: VoiceAssembly,
    private val setups: SpeechSetups,
    private val readiness: VoiceReadiness,
    private val audio: AudioSource?,
    private val log: LumbinaLog,
) {
    /** Null until recognition is ready. */
    private var controller: VoiceController? = null

    /** Speech for other apps; null until recognition is ready. */
    var forApps: AppRecognition? = null
        private set

    private var ready = false
    private val modelsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Custom app names may have changed in the launcher's process: read them again. */
    private val namesStale = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** From the launcher: a speech model was installed or deleted, or an on-screen mic key pressed. */
    private val fromLauncher = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                LumbinaActions.SPEECH_MODELS_CHANGED -> modelsChanged.tryEmit(Unit)

                LumbinaActions.TOGGLE_VOICE -> {
                    namesStale.tryEmit(Unit)
                    controller?.toggle()
                }
            }
        }
    }

    /**
     * Starts following settings and models. [onAvailable] hears whether voice is ready: the mic
     * key is taken over while recognition is ready, and handed back to the firmware otherwise.
     */
    fun start(scope: CoroutineScope, onAvailable: (Boolean) -> Unit) {
        val filter = IntentFilter().apply {
            addAction(LumbinaActions.SPEECH_MODELS_CHANGED)
            addAction(LumbinaActions.TOGGLE_VOICE)
        }
        ContextCompat.registerReceiver(service, fromLauncher, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val audio = audio ?: return log.log(TAG, "no remote mic on this device")
        val models = modelsChanged.onStart { emit(Unit) }
        scope.launch {
            combine(setups.setups(namesStale), models) { setup, _ -> setup }
                .collectLatest { setup ->
                    val nowReady = readiness.prepare(setup)
                    if (nowReady && controller == null) build(scope, audio)
                    if (nowReady != ready) {
                        ready = nowReady
                        onAvailable(nowReady)
                    }
                }
        }
    }

    /** Listens until [release], with the apps' names as they are now. */
    fun listen() {
        namesStale.tryEmit(Unit)
        controller?.start()
    }

    fun release() {
        controller?.stop()
    }

    /** A notice of voice input's that OK acts on has the OK while it shows; true if taken. */
    fun onKey(event: KeyEvent): Boolean = controller?.onKey(event) == true

    /** What [listen] started was not meant: drops it quietly. */
    fun cancel() {
        controller?.cancel()
    }

    fun cycleLanguage() {
        controller?.cycleLanguage()
    }

    fun stop() {
        forApps?.let(AppRecognition::retire)
        controller?.dispose()
        service.unregisterReceiver(fromLauncher)
    }

    private fun build(scope: CoroutineScope, audio: AudioSource) {
        val language = assembly.language(scope)
        controller = assembly.controller(scope, audio, language)
        forApps = assembly.forApps(audio, language).also(AppRecognition::offer)
    }

    private companion object {
        const val TAG = "a11y"
    }
}
