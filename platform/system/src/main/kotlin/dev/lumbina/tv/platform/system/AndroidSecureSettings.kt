package dev.lumbina.tv.platform.system

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.OUR_ACCESSIBILITY_SERVICE
import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import dev.lumbina.tv.core.domain.health.SecureSettings
import javax.inject.Inject
import javax.inject.Named

internal class AndroidSecureSettings @Inject constructor(@ApplicationContext private val context: Context) :
    SecureSettings {

    override val canWrite: Boolean
        get() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    override fun enabledAccessibilityServices(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    override fun setEnabledAccessibilityServices(value: String) {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)
    }

    override fun setAccessibilityEnabled() {
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }
}

/**
 * Asks the system which accessibility services are actually running. Being listed in
 * `enabled_accessibility_services` is not enough: after a reboot the firmware may list
 * the service without binding it.
 */
internal class AndroidAccessibilityHealth @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named(OUR_ACCESSIBILITY_SERVICE) private val ourService: String,
) : AccessibilityHealth {

    override fun isServiceBound(): Boolean {
        val ours = ComponentName.unflattenFromString(ourService) ?: return false
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == ours }
    }
}
