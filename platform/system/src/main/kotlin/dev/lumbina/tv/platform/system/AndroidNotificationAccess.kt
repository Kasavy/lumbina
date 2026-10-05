package dev.lumbina.tv.platform.system

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.access.NotificationAccess
import javax.inject.Inject

/** Whether Lumbina's notification listener is allowed, by the system's list of allowed packages. */
internal class AndroidNotificationAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screens: SettingsScreens,
) : NotificationAccess {
    override fun granted(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    override fun openSettings(): Boolean = screens.openSpecialAccess(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
