package dev.lumbina.tv.core.domain.remote

/**
 * Which action a gesture on a button triggers. A key with no bindings is passed through to the
 * system; so is one of [lumbinaOnly] while another app is on screen: there it does what it does
 * without Lumbina.
 */
class KeyBindingPolicy(
    private val bindings: Map<Pair<RemoteKey, KeyGesture>, RemoteAction>,
    private val lumbinaOnly: Set<RemoteKey> = emptySet(),
) {

    fun actionFor(key: RemoteKey, gesture: KeyGesture): RemoteAction? = bindings[key to gesture]

    /** [inLumbina]: one of Lumbina's own screens is in front. */
    fun handles(key: RemoteKey, inLumbina: Boolean = true): Boolean = handles(key) { inLumbina }

    /** [inLumbina] is asked only for a key it decides: looking at the screen is not free. */
    fun handles(key: RemoteKey, inLumbina: () -> Boolean): Boolean =
        bindings.keys.any { it.first == key } && (key !in lumbinaOnly || inLumbina())

    /** True if the key has a long-press binding, so a short press must wait for key up. */
    fun hasLongPress(key: RemoteKey): Boolean = (key to KeyGesture.LongPress) in bindings

    companion object {
        /**
         * What the keys do before the user changes anything: Home opens Lumbina, a double press
         * shows recent apps, a long press the quick menu. Menu shows the quick menu too, on
         * Lumbina's own screens ([DEFAULT_LUMBINA_ONLY]): in an app it is the app's. With voice
         * input available, the mic button is push-to-talk; otherwise it stays with the firmware
         * (e.g. its assistant).
         */
        fun defaultBindings(voiceAvailable: Boolean): Map<Pair<RemoteKey, KeyGesture>, RemoteAction> = buildMap {
            put(RemoteKey.Home to KeyGesture.ShortPress, RemoteAction.OpenLumbina)
            put(RemoteKey.Home to KeyGesture.DoubleTap, RemoteAction.ShowAppSwitcher)
            put(RemoteKey.Home to KeyGesture.LongPress, RemoteAction.ShowQuickMenu)
            put(RemoteKey.Menu to KeyGesture.ShortPress, RemoteAction.ShowQuickMenu)
            if (voiceAvailable) {
                // Hold to talk; a quick tap is no speech, a double tap switches the language mode.
                put(RemoteKey.Mic to KeyGesture.Down, RemoteAction.StartVoice)
                put(RemoteKey.Mic to KeyGesture.Up, RemoteAction.StopVoice)
                put(RemoteKey.Mic to KeyGesture.Tap, RemoteAction.CancelVoice)
                put(RemoteKey.Mic to KeyGesture.DoubleTap, RemoteAction.CycleVoiceLanguage)
            }
        }

        /** The buttons that are Lumbina's only on its own screens until the user says otherwise. */
        val DEFAULT_LUMBINA_ONLY = setOf(RemoteKey.Menu)

        /**
         * The defaults with the user's [own] bindings over them: each sets what a gesture does,
         * or takes it off ("as on the device"). Bindings of keys that can't be bound, and of
         * gestures a user has no say in, are passed over: a stored one must not take the arrows away.
         * The mic is held to talk: only its double press is the user's, and only while Lumbina
         * takes the button at all ([voiceAvailable]).
         */
        fun defaults(
            voiceAvailable: Boolean = false,
            own: List<KeyBinding> = emptyList(),
            lumbinaOnly: Set<RemoteKey> = DEFAULT_LUMBINA_ONLY,
        ): KeyBindingPolicy {
            val bindings = defaultBindings(voiceAvailable).toMutableMap()
            own.filter { it.applies(voiceAvailable) }.forEach { binding ->
                val at = binding.key to binding.gesture
                if (binding.action == null) bindings.remove(at) else bindings[at] = binding.action
            }
            // Home is how Lumbina is come back to: it is never limited to Lumbina's own screens.
            return KeyBindingPolicy(bindings, lumbinaOnly - RemoteKey.Home)
        }
    }
}

private fun KeyBinding.applies(voiceAvailable: Boolean): Boolean = when {
    gesture !in BINDABLE_GESTURES -> false
    key == RemoteKey.Mic -> voiceAvailable && gesture == KeyGesture.DoubleTap
    else -> key.canBeBound
}
