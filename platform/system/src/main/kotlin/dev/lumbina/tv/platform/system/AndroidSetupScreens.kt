package dev.lumbina.tv.platform.system

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import dev.lumbina.tv.core.domain.access.SetupScreens
import javax.inject.Inject

internal class AndroidSetupScreens @Inject constructor(private val screens: SettingsScreens) : SetupScreens {

    override val hasAccessibilitySettings: Boolean
        get() = accessibilityScreens().any { screens.target(it) != null }

    /** The first that starts: a resolved screen may still fail to (TV Settings' on the Mi Box 4). */
    override fun openAccessibilitySettings(): Boolean = accessibilityScreens().any(screens::start)

    override fun openDeveloperSettings(): Boolean =
        screens.start(screens.intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))

    /**
     * The accessibility screen. Android TV 9 (Mi Box 4) answers its action with a stub that only
     * says "not supported": then TV Settings' own accessibility screen, which some versions have,
     * and failing that the settings themselves, which have accessibility among the device's settings.
     */
    private fun accessibilityScreens(): List<Intent> {
        val direct = screens.intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val known = screens.target(direct) != null
        if (known && !screens.isStub(direct)) return listOf(direct)
        val fallbacks = listOf(
            screens.intent(Intent.ACTION_MAIN).setComponent(TV_ACCESSIBILITY),
            screens.intent(Settings.ACTION_SETTINGS),
        )
        // Unresolved is unknown, not missing (package visibility): try it first.
        return if (known) fallbacks else listOf(direct) + fallbacks
    }

    private companion object {
        val TV_ACCESSIBILITY =
            ComponentName("com.android.tv.settings", "com.android.tv.settings.system.AccessibilityActivity")
    }
}
