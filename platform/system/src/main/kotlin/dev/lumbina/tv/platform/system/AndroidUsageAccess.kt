package dev.lumbina.tv.platform.system

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.access.UsageAccess
import javax.inject.Inject

/** Usage access by its app op: TVs rarely offer it in their settings, adb grants it too. */
internal class AndroidUsageAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screens: SettingsScreens,
) : UsageAccess {
    override fun granted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val op = AppOpsManager.OPSTR_GET_USAGE_STATS
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(op, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun openSettings(): Boolean = screens.openSpecialAccess(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
