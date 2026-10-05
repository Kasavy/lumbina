package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.backdrop.CurrentWallpaper
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.backdrop.isNightHour
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** [nightDim]: extra darkness for the evening and night (0 by day or when switched off). */
data class WallpaperUi(
    val item: WallpaperItem? = null,
    val nightDim: Float = 0f,
    val background: HomeBackground = HomeBackground.Night,
)

@HiltViewModel
class WallpaperViewModel @Inject constructor(
    wallpaper: CurrentWallpaper,
    settings: WallpaperSettingsRepository,
    appearance: HomeAppearanceRepository,
) : ViewModel() {

    private val hours = flow {
        while (true) {
            emit(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            delay(CHECK_MILLIS)
        }
    }

    val state: StateFlow<WallpaperUi> = combine(
        wallpaper.items(),
        settings.settings,
        hours,
        appearance.settings,
    ) { item, choice, hour, look ->
        WallpaperUi(item, if (choice.dimAtNight && isNightHour(hour)) NIGHT_DIM else 0f, look.background)
    }.stateIn(viewModelScope, WhileUiSubscribed, WallpaperUi())

    private companion object {
        const val CHECK_MILLIS = 5 * 60_000L
        const val NIGHT_DIM = 0.25f
    }
}
