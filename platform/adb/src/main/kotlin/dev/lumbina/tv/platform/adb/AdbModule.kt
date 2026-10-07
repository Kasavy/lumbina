package dev.lumbina.tv.platform.adb

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.common.LumbinaComponents
import dev.lumbina.tv.core.domain.access.CompanionApp
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.LocalAdb
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.diagnostics.AskDevice
import dev.lumbina.tv.core.domain.health.HealAccessibilityService
import dev.lumbina.tv.core.domain.health.SecureSettings

@Module
@InstallIn(SingletonComponent::class)
internal interface AdbModule {
    @Binds fun localAdb(impl: AndroidLocalAdb): LocalAdb

    companion object {
        @Provides
        fun askDevice(profile: DeviceProfile, adb: LocalAdb): AskDevice = AskDevice(profile, adb)

        @Provides
        fun localAccess(
            adb: LocalAdb,
            secureSettings: SecureSettings,
            heal: HealAccessibilityService,
            companions: Set<@JvmSuppressWildcards CompanionApp>,
            @ApplicationContext context: Context,
        ): LocalAccess = LocalAccess(
            adb,
            secureSettings,
            heal,
            context.packageName,
            LumbinaComponents.NOTIFICATION_LISTENER,
            companions.toList(),
        )
    }
}
