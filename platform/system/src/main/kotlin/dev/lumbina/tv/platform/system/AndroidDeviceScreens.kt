package dev.lumbina.tv.platform.system

import android.provider.Settings
import dev.lumbina.tv.core.domain.status.DeviceScreens
import javax.inject.Inject

internal class AndroidDeviceScreens @Inject constructor(private val screens: SettingsScreens) : DeviceScreens {

    override fun openNetwork(): Boolean = open(
        Settings.ACTION_WIFI_SETTINGS,
        Settings.ACTION_WIRELESS_SETTINGS,
        Settings.ACTION_SETTINGS,
    )

    override fun hasSettings(): Boolean = !screens.isStub(screens.intent(Settings.ACTION_SETTINGS))

    override fun openSettings(): Boolean = open(Settings.ACTION_SETTINGS)

    /** The first of [actions] the firmware answers with a screen of its own. */
    private fun open(vararg actions: String): Boolean =
        actions.map(screens::intent).filterNot(screens::isStub).any(screens::start)
}
