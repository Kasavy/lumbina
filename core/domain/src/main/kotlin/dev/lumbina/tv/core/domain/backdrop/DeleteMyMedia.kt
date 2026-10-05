package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import javax.inject.Inject

/**
 * Deletes the user's own pictures and videos: the wallpaper falls back to the gradient if it
 * showed them, and banners made from them go back to the apps' own. Blocking file work: call
 * it off the main thread.
 */
class DeleteMyMedia @Inject constructor(
    private val folder: PhotoFolder,
    private val appearance: HomeAppearanceRepository,
    private val settings: WallpaperSettingsRepository,
) {
    suspend operator fun invoke(now: Long) {
        folder.clear()
        appearance.update { look ->
            look.copy(customBanners = look.customBanners.filterValues { !folder.contains(it) })
        }
        settings.update {
            val source = if (it.source == WallpaperSource.MyMedia) WallpaperSource.Gradient else it.source
            it.copy(source = source, mediaChangedAt = now)
        }
    }
}
