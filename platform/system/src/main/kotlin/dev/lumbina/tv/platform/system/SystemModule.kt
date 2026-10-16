package dev.lumbina.tv.platform.system

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.NotificationAccess
import dev.lumbina.tv.core.domain.access.SetupScreens
import dev.lumbina.tv.core.domain.access.UsageAccess
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.app.AppSystemActions
import dev.lumbina.tv.core.domain.backdrop.ScreensaverSetup
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.PackageStates
import dev.lumbina.tv.core.domain.diagnostics.LogArchive
import dev.lumbina.tv.core.domain.diagnostics.ReportExporter
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import dev.lumbina.tv.core.domain.health.HealLock
import dev.lumbina.tv.core.domain.health.SecureSettings
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.domain.phone.LocalAddress
import dev.lumbina.tv.core.domain.remote.HomeScreens
import dev.lumbina.tv.core.domain.remote.KeyCapture
import dev.lumbina.tv.core.domain.remote.LumbinaHome
import dev.lumbina.tv.core.domain.speech.SpeechApps
import dev.lumbina.tv.core.domain.status.ConnectivityStatus
import dev.lumbina.tv.core.domain.status.DeviceScreens
import dev.lumbina.tv.core.domain.status.MediaSessions
import dev.lumbina.tv.core.domain.status.NotificationFeed
import dev.lumbina.tv.core.domain.text.SystemSearch
import dev.lumbina.tv.core.domain.text.YouTubeApps
import dev.lumbina.tv.core.domain.text.YouTubeSearch

// One @Binds per port this module adapts; splitting it would only scatter the wiring.
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
internal interface SystemModule {
    @Binds fun fingerprint(impl: AndroidDeviceFingerprint): DeviceFingerprint

    @Binds fun packageStates(impl: AndroidPackageStates): PackageStates

    @Binds fun homeScreens(impl: AndroidHomeScreens): HomeScreens

    @Binds fun keyCapture(impl: AndroidKeyCapture): KeyCapture

    @Binds fun secureSettings(impl: AndroidSecureSettings): SecureSettings

    @Binds fun health(impl: AndroidAccessibilityHealth): AccessibilityHealth

    @Binds fun healLock(impl: FileHealLock): HealLock

    @Binds fun log(impl: FileLumbinaLog): LumbinaLog

    @Binds fun logArchive(impl: FileLumbinaLog): LogArchive

    @Binds fun systemInfo(impl: AndroidSystemInfo): SystemInfo

    @Binds fun reportExporter(impl: AndroidReportExporter): ReportExporter

    @Binds fun screensaverSetup(impl: AndroidScreensaverSetup): ScreensaverSetup

    @Binds fun appLauncher(impl: AndroidAppLauncher): AppLauncher

    @Binds fun appSystemActions(impl: AndroidAppSystemActions): AppSystemActions

    @Binds fun lumbinaHome(impl: AndroidLumbinaHome): LumbinaHome

    @Binds fun youTubeSearch(impl: AndroidYouTubeSearch): YouTubeSearch

    @Binds fun youTubeApps(impl: AndroidYouTubeApps): YouTubeApps

    @Binds fun speechApps(impl: AndroidSpeechApps): SpeechApps

    @Binds fun systemSearch(impl: AndroidSystemSearch): SystemSearch

    @Binds fun connectivity(impl: AndroidConnectivityStatus): ConnectivityStatus

    @Binds fun deviceScreens(impl: AndroidDeviceScreens): DeviceScreens

    @Binds fun notifications(impl: AndroidNotificationFeed): NotificationFeed

    @Binds fun localAddress(impl: AndroidLocalAddress): LocalAddress

    @Binds fun setupScreens(impl: AndroidSetupScreens): SetupScreens

    @Binds fun notificationAccess(impl: AndroidNotificationAccess): NotificationAccess

    @Binds fun usageAccess(impl: AndroidUsageAccess): UsageAccess

    @Binds fun mediaSessions(impl: AndroidMediaSessions): MediaSessions

    @Binds fun appLanguage(impl: AndroidAppLanguage): AppLanguage
}
