package dev.lumbina.tv.core.domain.remote

/** Remote-control buttons Lumbina can take over. Vendors map raw key events to these via [KeyMap]. */
enum class RemoteKey { Home, Mic }

/** A raw key event as the platform delivers it. */
data class RawKey(val scanCode: Int, val keyCode: Int, val action: KeyAction)

enum class KeyAction { Down, Up }

/** Translates raw key events of a device family into [RemoteKey]s; `null` means "not ours". */
fun interface KeyMap {
    fun map(scanCode: Int, keyCode: Int): RemoteKey?
}

/**
 * [ShortPress]/[LongPress] are decided on release/after a hold. [Down]/[Up] fire immediately
 * and suit push-to-talk; for such keys a very short press is a [Tap] instead of [Up]. A second
 * quick press fires [DoubleTap]: after a [Tap], or instead of a [ShortPress] (which then waits
 * to see whether one follows).
 */
enum class KeyGesture { ShortPress, LongPress, Down, Up, Tap, DoubleTap }

/** Brings Lumbina's home screen to the front; at its first tile if `toStart` (e.g. on power-on). */
fun interface LumbinaHome {
    fun open(toStart: Boolean)
}

/** What Lumbina does in response to a button gesture. */
sealed interface RemoteAction {
    data object OpenLumbina : RemoteAction

    /** Sleep timer, volume, the device's settings, the stock launcher: over whatever is on screen. */
    data object ShowQuickMenu : RemoteAction
    data object StartVoice : RemoteAction
    data object StopVoice : RemoteAction

    /** The mic was only tapped: drop the utterance quietly. */
    data object CancelVoice : RemoteAction
    data object CycleVoiceLanguage : RemoteAction

    /** Recent apps, to switch between them. */
    data object ShowAppSwitcher : RemoteAction
}
