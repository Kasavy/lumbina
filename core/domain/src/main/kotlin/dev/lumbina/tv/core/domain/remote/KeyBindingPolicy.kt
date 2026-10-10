package dev.lumbina.tv.core.domain.remote

/** Which action a gesture on a button triggers. A key with no bindings is passed through to the system. */
class KeyBindingPolicy(private val bindings: Map<Pair<RemoteKey, KeyGesture>, RemoteAction>) {

    fun actionFor(key: RemoteKey, gesture: KeyGesture): RemoteAction? = bindings[key to gesture]

    fun handles(key: RemoteKey): Boolean = bindings.keys.any { it.first == key }

    /** True if the key has a long-press binding, so a short press must wait for key up. */
    fun hasLongPress(key: RemoteKey): Boolean = (key to KeyGesture.LongPress) in bindings

    companion object {
        /**
         * Home opens Lumbina, a double press shows recent apps if [doubleHomeRecents], a long
         * press the quick menu.
         * With voice input available, the mic button is push-to-talk; otherwise it stays with
         * the firmware (e.g. its assistant).
         */
        fun defaults(voiceAvailable: Boolean = false, doubleHomeRecents: Boolean = true): KeyBindingPolicy =
            KeyBindingPolicy(
                buildMap {
                    put(RemoteKey.Home to KeyGesture.ShortPress, RemoteAction.OpenLumbina)
                    if (doubleHomeRecents) put(RemoteKey.Home to KeyGesture.DoubleTap, RemoteAction.ShowAppSwitcher)
                    put(RemoteKey.Home to KeyGesture.LongPress, RemoteAction.ShowQuickMenu)
                    if (voiceAvailable) {
                        // Hold to talk; a quick tap is no speech, a double tap switches the language mode.
                        put(RemoteKey.Mic to KeyGesture.Down, RemoteAction.StartVoice)
                        put(RemoteKey.Mic to KeyGesture.Up, RemoteAction.StopVoice)
                        put(RemoteKey.Mic to KeyGesture.Tap, RemoteAction.CancelVoice)
                        put(RemoteKey.Mic to KeyGesture.DoubleTap, RemoteAction.CycleVoiceLanguage)
                    }
                },
            )
    }
}
