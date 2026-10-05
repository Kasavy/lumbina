package dev.lumbina.tv.platform.system

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import javax.inject.Inject

internal class AndroidDeviceFingerprint @Inject constructor(@ApplicationContext private val context: Context) :
    DeviceFingerprint {
    override val manufacturer: String = Build.MANUFACTURER.orEmpty()
    override val model: String = Build.MODEL.orEmpty()

    override fun isInstalled(packageName: String): Boolean = context.packageManager.isInstalled(packageName)
}
