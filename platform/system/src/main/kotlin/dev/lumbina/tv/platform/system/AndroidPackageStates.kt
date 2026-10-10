package dev.lumbina.tv.platform.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.device.PackageState
import dev.lumbina.tv.core.domain.device.PackageStates
import javax.inject.Inject

/** A package Lumbina can't see (no `queries` entry for it) reads as missing. */
internal class AndroidPackageStates @Inject constructor(@ApplicationContext private val context: Context) :
    PackageStates {

    override fun of(packageName: String): PackageState {
        val app = try {
            // A package uninstalled for the user is still the system's: asked for by name, it is told apart.
            context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (_: PackageManager.NameNotFoundException) {
            return PackageState.Missing
        }
        return when {
            app.flags and ApplicationInfo.FLAG_INSTALLED == 0 -> PackageState.Removed
            app.enabled -> PackageState.Enabled
            else -> PackageState.Disabled
        }
    }
}
