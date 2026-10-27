package dev.lumbina.tv.feature.settings

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.lumbina.tv.core.domain.remote.KeyBindingPolicy
import dev.lumbina.tv.core.domain.remote.KeyGesture
import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.remote.RemoteKey

/** A button's name: the user's own, what its key code is known as, or "Button 251". */
@Composable
internal fun ButtonsState.nameOf(key: RemoteKey): String {
    val known = when (key) {
        RemoteKey.Home -> R.string.key_home
        RemoteKey.Mic -> R.string.key_mic
        else -> KEY_NAMES[key.keyCode]
    }
    val usual = if (known != null) stringResource(known) else stringResource(R.string.key_numbered, key.number)
    return settings.keyNames[key] ?: usual
}

/** The number a button with no name of its own is called by. */
private val RemoteKey.number: String get() = id.substringAfter(':')

@Composable
internal fun KeyGesture.label(): String = stringResource(
    when (this) {
        KeyGesture.DoubleTap -> R.string.gesture_double
        KeyGesture.LongPress -> R.string.gesture_hold
        else -> R.string.gesture_press
    },
)

/** What [gesture] on [key] does now: the user's choice, else Lumbina's own; null: nothing of Lumbina's. */
internal fun ButtonsState.actionOf(key: RemoteKey, gesture: KeyGesture): RemoteAction? {
    val own = settings.bindings.firstOrNull { it.key == key && it.gesture == gesture }
    // The mic's own gestures count as they are with voice input there: its row is shown only where Lumbina hears it.
    val usual = KeyBindingPolicy.defaultBindings(voiceAvailable = true)[key to gesture]
    return if (own != null) own.action else usual
}

/** An action in a few words; an app by its name, or as gone when it is no longer installed. */
@Composable
internal fun ButtonsState.labelOf(action: RemoteAction?): String = when (action) {
    null -> stringResource(R.string.action_as_device)

    is RemoteAction.OpenApp ->
        apps.firstOrNull { it.id == action.app }?.label ?: stringResource(R.string.action_app_gone)

    else -> stringResource(ACTION_NAMES[action] ?: R.string.action_as_device)
}

/** Whether [action] opens an app that is no longer there: the button then works as on the device. */
internal fun ButtonsState.isGone(action: RemoteAction?): Boolean =
    action is RemoteAction.OpenApp && apps.none { it.id == action.app }

internal val ACTION_NAMES: Map<RemoteAction, Int> = mapOf(
    RemoteAction.OpenLumbina to R.string.action_home,
    RemoteAction.OpenAllApps to R.string.action_all_apps,
    RemoteAction.ShowAppSwitcher to R.string.action_recent,
    RemoteAction.ShowQuickMenu to R.string.action_quick_menu,
    RemoteAction.OpenLumbinaSettings to R.string.action_settings,
    RemoteAction.OpenSearch to R.string.action_search,
    RemoteAction.ShowSleepTimer to R.string.action_sleep_timer,
    RemoteAction.ShowNotifications to R.string.action_notifications,
    RemoteAction.ShowScreensaver to R.string.action_screensaver,
    RemoteAction.OpenPreviousApp to R.string.action_previous_app,
    RemoteAction.CycleVoiceLanguage to R.string.action_voice_language,
    RemoteAction.MediaPlayPause to R.string.action_play_pause,
    RemoteAction.MediaNext to R.string.action_next,
    RemoteAction.MediaPrevious to R.string.action_previous,
    RemoteAction.OpenOtherHome to R.string.action_other_home,
    RemoteAction.OpenDeviceSettings to R.string.action_device_settings,
    RemoteAction.ScreenOff to R.string.action_screen_off,
    RemoteAction.ToggleMute to R.string.action_mute,
    RemoteAction.Nothing to R.string.action_nothing,
)

// The buttons a TV remote is likely to have besides the ones Lumbina keeps.
private val KEY_NAMES: Map<Int?, Int> = mapOf(
    KeyEvent.KEYCODE_MENU to R.string.key_menu,
    KeyEvent.KEYCODE_PROG_RED to R.string.key_red,
    KeyEvent.KEYCODE_PROG_GREEN to R.string.key_green,
    KeyEvent.KEYCODE_PROG_YELLOW to R.string.key_yellow,
    KeyEvent.KEYCODE_PROG_BLUE to R.string.key_blue,
    KeyEvent.KEYCODE_TV_INPUT to R.string.key_input,
    KeyEvent.KEYCODE_SETTINGS to R.string.key_settings,
    KeyEvent.KEYCODE_INFO to R.string.key_info,
    KeyEvent.KEYCODE_GUIDE to R.string.key_guide,
    KeyEvent.KEYCODE_TV to R.string.key_tv,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to R.string.key_play_pause,
    KeyEvent.KEYCODE_BOOKMARK to R.string.key_bookmark,
    KeyEvent.KEYCODE_CHANNEL_UP to R.string.key_channel_up,
    KeyEvent.KEYCODE_CHANNEL_DOWN to R.string.key_channel_down,
)
