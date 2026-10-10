package dev.lumbina.tv.platform.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.app.LaunchHistory
import javax.inject.Inject

internal class AndroidAppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
    private val history: LaunchHistory,
) : AppLauncher {

    /**
     * Starts the app exactly as a system launcher does (MAIN plus its launcher category), so an
     * app already running comes back where it was: Android only resumes the existing task when
     * the intent matches the one that started it, otherwise some apps (e.g. Kinopoisk) open a
     * fresh screen on top.
     */
    override fun launch(id: AppId): Boolean {
        val component = ComponentName(id.packageName, id.activityName)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(launcherCategory(component))
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            history.record(id.packageName)
            true
        } catch (e: ActivityNotFoundException) {
            log.log(TAG, "launch ${id.flatten()} failed: $e")
            false
        } catch (e: SecurityException) {
            log.log(TAG, "launch ${id.flatten()} not allowed: $e")
            false
        }
    }

    /**
     * The TV launcher category if this activity is the app's TV entry, else the phone one. A home
     * screen that is in no launcher is started as one: an app may refuse an intent its activity
     * declared no filter for.
     */
    private fun launcherCategory(component: ComponentName): String {
        val packages = context.packageManager
        val name = component.packageName
        if (packages.getLeanbackLaunchIntentForPackage(name)?.component == component) {
            return Intent.CATEGORY_LEANBACK_LAUNCHER
        }
        if (packages.getLaunchIntentForPackage(name)?.component == component) return Intent.CATEGORY_LAUNCHER
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(name)
        val isHome = packages.queryIntentActivities(home, 0).any { it.activityInfo.name == component.className }
        return if (isHome) Intent.CATEGORY_HOME else Intent.CATEGORY_LAUNCHER
    }

    private companion object {
        const val TAG = "launcher"
    }
}
