package dev.lumbina.tv.platform.accessibility

/**
 * Whose the remote's mic key is right now: an app's that records the remote ([MicInjection]), an
 * app's that waits for speech ([AppRecognition]), or else voice input's. Main thread.
 */
internal class MicKey(private val voice: RemoteVoice, private val micInjection: MicInjection?) {

    /** Voice input listens, unless the key is an app's: one waiting for speech listens on while it is held. */
    fun pressed() {
        if (recorded) return
        val app = voice.forApps
        if (app?.active == true) {
            app.keyHeld = true
            return
        }
        voice.listen()
    }

    /** Debug builds: the key over adb, as injected keys do not reach the service. */
    fun debug(command: String) = when (command) {
        "down" -> pressed()
        "up" -> released()
        else -> tapped()
    }

    fun released() {
        voice.forApps?.keyHeld = false
        voice.release()
    }

    /** The press was only a tap. */
    fun tapped() {
        voice.forApps?.keyHeld = false
        voice.cancel()
    }

    /** Switches voice input's languages, but not while the key is an app's: there taps are only that app's. */
    fun doubleTapped() {
        if (recorded || voice.forApps?.active == true) return
        voice.cycleLanguage()
    }

    private val recorded: Boolean get() = micInjection?.feeding == true
}
