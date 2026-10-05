package dev.lumbina.tv.platform.system

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.OUR_SCREENSAVER
import dev.lumbina.tv.core.domain.backdrop.ScreensaverSetup
import javax.inject.Inject
import javax.inject.Named

/**
 * Reads and (with WRITE_SECURE_SETTINGS, granted over adb) sets the system screensaver
 * ("daydream") to Lumbina. Android TV has no screen for this on many devices.
 */
internal class AndroidScreensaverSetup @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named(OUR_SCREENSAVER) private val component: String,
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

    private companion object {
        const val COMPONENTS = "screensaver_components"
        const val ENABLED = "screensaver_enabled"
    }
}
