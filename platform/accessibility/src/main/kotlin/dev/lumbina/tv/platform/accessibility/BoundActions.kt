package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.content.Intent
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.status.DeviceScreens
import dev.lumbina.tv.core.domain.voice.DeviceControls
import dev.lumbina.tv.core.domain.voice.MediaAction
import dev.lumbina.tv.core.domain.voice.VolumeChange

/**
 * What a button the user gave an action to does, besides what Home and the mic do by themselves:
 * an app, a screen of Lumbina's, the player, the device. What could not be opened is said at the
 * bottom of the screen ([say]).
 */
internal class BoundActions(
    private val context: Context,
    private val launcher: AppLauncher,
    private val controls: DeviceControls,
    private val screens: DeviceScreens,
    private val otherHome: () -> HomeScreen?,
    private val say: (String) -> Unit,
) {
    /** False for an action that is not one of these. */
    fun perform(action: RemoteAction): Boolean {
        PLACES[action]?.let {
            context.openLumbina(it)
            return true
        }
        when (action) {
            RemoteAction.ShowScreensaver -> showScreensaver()
            is RemoteAction.OpenApp -> if (!launcher.launch(action.app)) say(context.getString(R.string.key_app_gone))
            RemoteAction.MediaPlayPause -> controls.media(MediaAction.PlayPause)
            RemoteAction.MediaNext -> controls.media(MediaAction.Next)
            RemoteAction.MediaPrevious -> controls.media(MediaAction.Previous)
            RemoteAction.OpenOtherHome -> openOtherHome()
            RemoteAction.OpenDeviceSettings -> screens.openSettings()
            RemoteAction.ScreenOff -> controls.standby()
            RemoteAction.ToggleMute -> controls.volume(VolumeChange.ToggleMute)
            RemoteAction.Nothing -> Unit
            else -> return false
        }
        return true
    }

    private fun showScreensaver() {
        val intent = Intent(LumbinaActions.SHOW_SCREENSAVER)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun openOtherHome() {
        val opened = otherHome()?.let { launcher.launch(it.id) } ?: false
        if (!opened) say(context.getString(R.string.quick_launcher_failed))
    }

    private companion object {
        /** The actions that open Lumbina at a place of its own. */
        val PLACES: Map<RemoteAction, String> = mapOf(
            RemoteAction.OpenAllApps to LumbinaActions.HOME_ALL_APPS,
            RemoteAction.OpenLumbinaSettings to LumbinaActions.SETTINGS_ROOT,
            RemoteAction.OpenSearch to LumbinaActions.HOME_SEARCH,
            RemoteAction.ShowSleepTimer to LumbinaActions.HOME_SLEEP_TIMER,
            RemoteAction.ShowNotifications to LumbinaActions.HOME_NOTICES,
        )
    }
}
