package dev.lumbina.tv.platform.system

import android.content.pm.PackageManager

/** Whether [packageName] is installed and visible to Lumbina. */
internal fun PackageManager.isInstalled(packageName: String): Boolean = try {
    getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
