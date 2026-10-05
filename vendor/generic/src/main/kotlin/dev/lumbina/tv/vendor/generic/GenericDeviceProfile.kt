package dev.lumbina.tv.vendor.generic

import android.view.KeyEvent
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.remote.KeyMap
import dev.lumbina.tv.core.domain.remote.RemoteKey
import javax.inject.Inject

/**
 * Any Android TV device. Home is the standard KEYCODE_HOME. Google TV ignores the HOME
 * role and always opens its own launcher, so the accessibility service takes over the
 * key here too; a long press opens the device's own launcher, found at runtime.
 */
class GenericDeviceProfile @Inject internal constructor(finder: StockLauncherFinder) : DeviceProfile {
    override val id = "generic"
    override val priority = 0

    override val keyMap = KeyMap { _, keyCode -> if (keyCode == KeyEvent.KEYCODE_HOME) RemoteKey.Home else null }

    override val stockLauncher: AppId? by lazy { finder.find() }

    override fun matches(device: DeviceFingerprint): Boolean = true
}
