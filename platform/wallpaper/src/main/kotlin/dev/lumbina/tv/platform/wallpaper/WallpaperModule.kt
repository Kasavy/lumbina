package dev.lumbina.tv.platform.wallpaper

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.domain.backdrop.BackdropPhotos
import dev.lumbina.tv.core.domain.backdrop.MediaFolder
import dev.lumbina.tv.core.domain.backdrop.PhotoFolder
import dev.lumbina.tv.core.domain.backdrop.PhotosOfTheDay
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins

@Module
@InstallIn(SingletonComponent::class)
internal interface WallpaperModule {
    @Binds fun photos(impl: LocalThenOnlinePhotos): BackdropPhotos

    @Binds fun folder(impl: AppPhotoFolder): PhotoFolder

    @Binds fun media(impl: AppPhotoFolder): MediaFolder

    @Binds fun photosOfTheDay(impl: BingPhotosOfTheDay): PhotosOfTheDay

    @Binds fun plugins(impl: ProjectivyPlugins): WallpaperPlugins
}
