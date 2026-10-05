package dev.lumbina.tv.platform.system

import android.content.pm.PackageManager

/** The name the system shows for [packageName]; the package itself for an app that is gone. */
internal fun PackageManager.labelOf(packageName: String): String = try {
    getApplicationLabel(getApplicationInfo(packageName, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    packageName
}
