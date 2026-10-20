package dev.lumbina.tv.platform.wallpaper

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.domain.backdrop.BackdropPhotos
import dev.lumbina.tv.core.domain.backdrop.MediaLibrary
import dev.lumbina.tv.core.domain.backdrop.PhotosOfTheDay
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal interface WallpaperModule {
    @Binds fun photos(impl: ScreensaverPhotoSource): BackdropPhotos

    @Binds fun library(impl: AppMediaLibrary): MediaLibrary

    @Binds fun photosOfTheDay(impl: BingPhotosOfTheDay): PhotosOfTheDay

    @Binds fun plugins(impl: ProjectivyPlugins): WallpaperPlugins
}

@Module
@InstallIn(SingletonComponent::class)
internal object WallpaperScopes {
    @Provides
    @Singleton
    @SavingScope
    fun saving(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
