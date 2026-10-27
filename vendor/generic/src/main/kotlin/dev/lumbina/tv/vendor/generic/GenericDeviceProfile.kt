package dev.lumbina.tv.vendor.generic

import android.view.KeyEvent
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.device.InputSources
import dev.lumbina.tv.core.domain.remote.KeyMap
import dev.lumbina.tv.core.domain.remote.RemoteKey
import javax.inject.Inject

/**
 * Any Android TV device. Home is the standard KEYCODE_HOME. Google TV ignores the HOME
 * role and always opens its own launcher, so the accessibility service takes over the
 * key here too. The device's own launcher is found at runtime.
 */
class GenericDeviceProfile @Inject internal constructor(finder: StockLauncherFinder, systemInputs: SystemInputs) :
    DeviceProfile {
    override val id = "generic"
    override val priority = 0

    override val keyMap = KeyMap { _, keyCode -> if (keyCode == KeyEvent.KEYCODE_HOME) RemoteKey.Home else null }

    override val stockLauncher: AppId? by lazy { finder.find() }

    /**
     * A TV's inputs where Android knows of them. Their list is asked later, and again while empty:
     * a TV may list them only a while after Lumbina starts. A box without TV features has none.
     */
    override val inputs: InputSources? by lazy { systemInputs.takeIf { it.isTv } }

    override fun matches(device: DeviceFingerprint): Boolean = true
}
