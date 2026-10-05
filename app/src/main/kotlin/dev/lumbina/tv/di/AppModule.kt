package dev.lumbina.tv.di

import android.content.ComponentName
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.common.ALL_INSTALLED_APPS
import dev.lumbina.tv.core.common.OUR_ACCESSIBILITY_SERVICE
import dev.lumbina.tv.core.common.OUR_SCREENSAVER
import dev.lumbina.tv.core.domain.app.AppsWithoutPlugins
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.device.DeviceProfileResolver
import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import dev.lumbina.tv.core.domain.health.HealAccessibilityService
import dev.lumbina.tv.core.domain.health.HealLock
import dev.lumbina.tv.core.domain.health.SecureSettings
import dev.lumbina.tv.core.domain.speech.CloudSpeechEngine
import dev.lumbina.tv.core.domain.speech.DeviceSpeech
import dev.lumbina.tv.core.domain.speech.SpeechEngine
import dev.lumbina.tv.core.domain.speech.SpeechEngineChoice
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import dev.lumbina.tv.core.network.HttpGet
import dev.lumbina.tv.core.network.UrlConnectionGet
import dev.lumbina.tv.feature.screensaver.LumbinaDream
import dev.lumbina.tv.platform.accessibility.LumbinaAccessibilityService
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** The device profile contributed by vendor modules that best matches this device. */
    @Provides
    @Singleton
    fun activeProfile(profiles: Set<@JvmSuppressWildcards DeviceProfile>, device: DeviceFingerprint): DeviceProfile =
        DeviceProfileResolver(profiles).resolve(device)

    @Provides
    @Named(OUR_ACCESSIBILITY_SERVICE)
    fun ourAccessibilityService(@ApplicationContext context: Context): String =
        ComponentName(context, LumbinaAccessibilityService::class.java).flattenToString()

    @Provides
    @Named(OUR_SCREENSAVER)
    fun ourScreensaver(@ApplicationContext context: Context): String =
        ComponentName(context, LumbinaDream::class.java).flattenToString()

    @Provides
    @Singleton
    fun installedApps(@Named(ALL_INSTALLED_APPS) all: InstalledApps, plugins: WallpaperPlugins): InstalledApps =
        AppsWithoutPlugins(all, plugins)

    /** One HTTP client for every platform adapter (weather, online photos). */
    @Provides
    fun httpGet(impl: UrlConnectionGet): HttpGet = impl

    /** The device's recognizer or a cloud one, as the voice settings choose. */
    @Provides
    @Singleton
    fun speechEngineChoice(
        @DeviceSpeech device: SpeechEngine,
        cloud: Map<SpeechProvider, @JvmSuppressWildcards CloudSpeechEngine>,
    ): SpeechEngineChoice = SpeechEngineChoice(device, cloud)

    @Provides
    fun healAccessibilityService(
        settings: SecureSettings,
        health: AccessibilityHealth,
        @Named(OUR_ACCESSIBILITY_SERVICE) ourService: String,
        lock: HealLock,
    ): HealAccessibilityService = HealAccessibilityService(settings, health, ourService, lock)
}
