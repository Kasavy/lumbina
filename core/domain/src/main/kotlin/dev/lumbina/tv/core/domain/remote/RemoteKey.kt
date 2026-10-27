package dev.lumbina.tv.core.domain.remote

import dev.lumbina.tv.core.domain.app.AppId
import kotlinx.coroutines.flow.Flow

/**
 * A button of the remote. [Home] and [Mic] are told by the device's profile ([KeyMap]), whatever
 * codes its remote sends for them; any other button is known by its key code ([ofCode]).
 */
data class RemoteKey(val id: String) {
    /** The key code of a button known only by it; null for [Home] and [Mic]. */
    val keyCode: Int? get() = id.removePrefix(CODE).takeIf { it != id }?.toIntOrNull()

    /**
     * Whether the user may give the button something to do. Not the arrows, OK and Back: the
     * remote does not work without them. Not power. Not the volume: its press and its hold must
     * stay the volume, and a key Lumbina takes for a double press is taken whole.
     */
    val canBeBound: Boolean get() = keyCode !in LOCKED

    companion object {
        val Home = RemoteKey("home")
        val Mic = RemoteKey("mic")
        private const val CODE = "key:"

        /** The remote's Menu button, by Android's key code for it. */
        val Menu = RemoteKey("${CODE}82")

        fun ofCode(keyCode: Int) = RemoteKey("$CODE$keyCode")

        /** A button the system gives no key code (0): told by what the remote itself sends for it. */
        fun ofScan(scanCode: Int) = RemoteKey("scan:$scanCode")

        /** The button a raw event is of, where the device's profile does not name it. */
        fun of(scanCode: Int, keyCode: Int): RemoteKey = if (keyCode != 0) ofCode(keyCode) else ofScan(scanCode)

        // Android's key codes (KeyEvent.KEYCODE_*; the domain does not see Android's classes).
        private const val DPAD_UP = 19
        private const val DPAD_DOWN = 20
        private const val DPAD_LEFT = 21
        private const val DPAD_RIGHT = 22
        private const val DPAD_CENTER = 23
        private const val DPAD_UP_LEFT = 268
        private const val DPAD_DOWN_LEFT = 269
        private const val DPAD_UP_RIGHT = 270
        private const val DPAD_DOWN_RIGHT = 271
        private const val ENTER = 66
        private const val NUMPAD_ENTER = 160
        private const val BUTTON_A = 96
        private const val BACK = 4
        private const val ESCAPE = 111
        private const val BUTTON_B = 97
        private const val POWER = 26
        private const val TV_POWER = 177
        private const val SLEEP = 223
        private const val WAKEUP = 224
        private const val VOLUME_UP = 24
        private const val VOLUME_DOWN = 25
        private const val VOLUME_MUTE = 164

        // The arrows and OK (a gamepad's A too), Back (and B), power and sleep, the volume.
        private val LOCKED = setOf(
            DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER,
            DPAD_UP_LEFT, DPAD_DOWN_LEFT, DPAD_UP_RIGHT, DPAD_DOWN_RIGHT,
            ENTER, NUMPAD_ENTER, BUTTON_A,
            BACK, ESCAPE, BUTTON_B,
            POWER, TV_POWER, SLEEP, WAKEUP,
            VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE,
        )
    }
}

/** A raw key event as the platform delivers it; [repeat]: a down the system sends again while the key is held. */
data class RawKey(val scanCode: Int, val keyCode: Int, val action: KeyAction, val repeat: Boolean = false)

enum class KeyAction { Down, Up }

/** Tells a device family's Home and mic keys by their raw events; `null` for any other key. */
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

/**
 * The buttons pressed on the remote while this is collected, for the settings to learn one by:
 * only the remote's service sees every key. A button named here was taken and did nothing else.
 */
interface KeyCapture {
    fun keys(): Flow<RemoteKey>

    /**
     * The gestures made on [key] while this is collected, for a button's page to show that a
     * double press and a hold are told apart. The button does nothing else meanwhile.
     */
    fun gestures(key: RemoteKey): Flow<KeyGesture>
}

/** Brings Lumbina's home screen to the front; at its first tile if `toStart` (e.g. on power-on). */
fun interface LumbinaHome {
    fun open(toStart: Boolean)
}

/** What Lumbina does in response to a button gesture. */
sealed interface RemoteAction {
    data object OpenLumbina : RemoteAction

    /** Sleep timer, the phone remote, the device's settings, the other launcher: over whatever is on screen. */
    data object ShowQuickMenu : RemoteAction
    data object StartVoice : RemoteAction
    data object StopVoice : RemoteAction

    /** The mic was only tapped: drop the utterance quietly. */
    data object CancelVoice : RemoteAction
    data object CycleVoiceLanguage : RemoteAction

    /** Recent apps, to switch between them. */
    data object ShowAppSwitcher : RemoteAction

    /** Lumbina's home screen at the section with every app. */
    data object OpenAllApps : RemoteAction
    data object OpenLumbinaSettings : RemoteAction

    /** Home's own windows, opened over it: the search, the sleep timer, the notifications. */
    data object OpenSearch : RemoteAction
    data object ShowSleepTimer : RemoteAction
    data object ShowNotifications : RemoteAction
    data object ShowScreensaver : RemoteAction

    /** The app that was on screen before this one, without the ribbon of recent apps. */
    data object OpenPreviousApp : RemoteAction

    data class OpenApp(val app: AppId) : RemoteAction

    data object MediaPlayPause : RemoteAction
    data object MediaNext : RemoteAction
    data object MediaPrevious : RemoteAction

    /** The other home screen: the device's own launcher unless the user chose another. */
    data object OpenOtherHome : RemoteAction
    data object OpenDeviceSettings : RemoteAction
    data object ScreenOff : RemoteAction

    /** The sound off, or back on: what a remote's mute key does. */
    data object ToggleMute : RemoteAction

    /** The button does nothing: to disarm one that is pressed by accident. */
    data object Nothing : RemoteAction
}

/**
 * What the user set a [gesture] on a [key] to do. A null [action] is "as on the device": what
 * Lumbina does by itself with that gesture is taken off, and a key left with no gesture is the
 * firmware's again.
 */
data class KeyBinding(val key: RemoteKey, val gesture: KeyGesture, val action: RemoteAction?)

/** The gestures a user can give an action to. */
val BINDABLE_GESTURES = listOf(KeyGesture.ShortPress, KeyGesture.DoubleTap, KeyGesture.LongPress)
