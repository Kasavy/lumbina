package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.backdrop.CurrentWallpaper
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.backdrop.nightFraction
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.domain.weather.WeatherSettingsRepository
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    weather: WeatherSettingsRepository,
) : ViewModel() {

    private val minutes = flow {
        while (true) {
            emit(ZonedDateTime.now())
            delay(CHECK_MILLIS)
        }
    }

    // From sunset to sunrise where the device is (the weather's place), coming and going smoothly.
    private val night = combine(minutes, weather.settings.map { it.known }.distinctUntilChanged()) { now, place ->
        nightFraction(now, place)
    }.distinctUntilChanged()

    val state: StateFlow<WallpaperUi> = combine(
        wallpaper.items(),
        settings.settings,
        night,
        appearance.settings,
    ) { item, choice, night, look ->
        WallpaperUi(item, if (choice.dimAtNight) NIGHT_DIM * night else 0f, look.background)
    }.stateIn(viewModelScope, WhileUiSubscribed, WallpaperUi())

    private companion object {
        const val CHECK_MILLIS = 60_000L
        const val NIGHT_DIM = 0.25f
    }
}
