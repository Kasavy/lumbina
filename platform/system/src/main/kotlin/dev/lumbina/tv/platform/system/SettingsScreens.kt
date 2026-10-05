package dev.lumbina.tv.platform.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Finds and starts the system's settings screens, where the firmware has them (some firmware hides most). */
internal class SettingsScreens @Inject constructor(@ApplicationContext private val context: Context) {

    fun intent(action: String): Intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** What answers [intent]; null is unknown, not missing (package visibility). */
    fun target(intent: Intent): ComponentName? = intent.resolveActivity(context.packageManager)

    /** Whether [intent] leads to Android TV's "not supported" screen. */
    fun isStub(intent: Intent): Boolean = target(intent)?.packageName == STUBS

    fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        // There but not exported (TV Settings' own screens on some firmware): try the next.
        false
    }

    /**
     * Opens a special access screen, or the apps' settings, where Android TV 9 (Mi Box 4) keeps it
     * under "special access" with no action of its own, or the settings; false when nothing opens.
     */
    fun openSpecialAccess(action: String): Boolean = listOf(
        intent(action),
        intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        intent(Settings.ACTION_SETTINGS),
    ).filterNot(::isStub).any(::start)

    private companion object {
        /** Android TV's catch-all for settings screens the device doesn't have. */
        const val STUBS = "com.google.android.tv.frameworkpackagestubs"
    }
}
