package dev.lumbina.tv.platform.packages

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.common.ALL_INSTALLED_APPS
import dev.lumbina.tv.core.domain.app.AppUsage
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.update.AppUpdates
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
internal interface PackagesModule {
    @Binds
    @Named(ALL_INSTALLED_APPS)
    fun installedApps(impl: AndroidInstalledApps): InstalledApps

    @Binds fun appUsage(impl: AndroidAppUsage): AppUsage

    @Binds fun appUpdates(impl: GitHubUpdates): AppUpdates
}
