package dev.lumbina.tv.platform.system

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.OUR_SCREENSAVER
import dev.lumbina.tv.core.domain.backdrop.ScreensaverPicker
import dev.lumbina.tv.core.domain.backdrop.ScreensaverSetup
import dev.lumbina.tv.core.domain.backdrop.screensaverPicker
import javax.inject.Inject
import javax.inject.Named

/**
 * Reads and (with WRITE_SECURE_SETTINGS, granted over adb) sets the system screensaver
 * ("daydream") to Lumbina. Without the permission, the device's own list of screensavers where it
 * has one: the user chooses Lumbina there.
 */
internal class AndroidScreensaverSetup @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named(OUR_SCREENSAVER) private val component: String,
    private val screens: SettingsScreens,
) : ScreensaverSetup {

    override val isLumbina: Boolean
        get() {
            val resolver = context.contentResolver
            val components = Settings.Secure.getString(resolver, COMPONENTS)?.split(',').orEmpty()
            return component in components && Settings.Secure.getInt(resolver, ENABLED, 0) == 1
        }

    /** False without WRITE_SECURE_SETTINGS; anything else is a bug and is not hidden. */
    override fun select(): Boolean = try {
        Settings.Secure.putString(context.contentResolver, COMPONENTS, component) &&
            Settings.Secure.putInt(context.contentResolver, ENABLED, 1)
    } catch (_: SecurityException) {
        false
    }

    override val picker: ScreensaverPicker?
        get() {
            val direct = screens.intent(Settings.ACTION_DREAM_SETTINGS)
            val main = screens.target(screens.intent(Settings.ACTION_SETTINGS))
            return screensaverPicker(
                screenOpens = screens.startable(direct) && !screens.isStub(direct),
                tvSettings = main?.packageName == SettingsScreens.TV_SETTINGS && main.className == TV_SETTINGS_MAIN,
                googleTv = isGoogleTv(),
            )
        }

    /** The list's own screen, or the settings where it fails to start (TV Settings' screens may not be exported). */
    override fun openPicker(): Boolean = when (picker) {
        ScreensaverPicker.Screen ->
            screens.start(screens.intent(Settings.ACTION_DREAM_SETTINGS)) ||
                screens.start(screens.intent(Settings.ACTION_SETTINGS))

        ScreensaverPicker.InSettings -> screens.start(screens.intent(Settings.ACTION_SETTINGS))

        null -> false
    }

    private fun isGoogleTv(): Boolean = try {
        context.packageManager.getPackageInfo(GOOGLE_TV_LAUNCHER, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val COMPONENTS = "screensaver_components"
        const val ENABLED = "screensaver_enabled"
        const val TV_SETTINGS_MAIN = "com.android.tv.settings.MainSettings"
        const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"
    }
}
