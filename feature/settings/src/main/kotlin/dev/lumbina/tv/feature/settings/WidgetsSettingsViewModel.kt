package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.home.ClockFormat
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.text.YouTube
import dev.lumbina.tv.core.domain.text.YouTubeApp
import dev.lumbina.tv.core.domain.text.YouTubeChoice
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.PlaceLocator
import dev.lumbina.tv.core.domain.weather.WeatherSettings
import dev.lumbina.tv.core.domain.weather.WeatherSettingsRepository
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WidgetsState(val appearance: HomeAppearance, val weather: WeatherSettings, val youTube: YouTubeChoice)

/** Where a city search is: not started, running, failed (e.g. offline) or found (maybe nothing). */
sealed interface CitySearch {
    data object Idle : CitySearch
    data object Searching : CitySearch
    data object Failed : CitySearch
    data class Found(val places: List<Place>) : CitySearch
}

/** What the widgets page can change. */
@Suppress("TooManyFunctions") // one per setting
interface WidgetsCommands {
    fun toggleDate()
    fun setClockFormat(format: ClockFormat)
    fun toggleWeather()
    fun setFahrenheit(fahrenheit: Boolean)
    fun toggleNowPlaying()
    fun searchCity(query: String)
    fun choosePlace(place: Place?)
    fun closeSearch()
    fun useYouTubeApp(app: YouTubeApp)
}

@Suppress("TooManyFunctions") // implements WidgetsCommands
@HiltViewModel
class WidgetsSettingsViewModel @Inject constructor(
    private val appearance: HomeAppearanceRepository,
    private val weather: WeatherSettingsRepository,
    private val places: PlaceLocator,
    private val youTube: YouTube,
) : ViewModel(),
    WidgetsCommands {

    private val search = MutableStateFlow<CitySearch>(CitySearch.Idle)
    val citySearch: StateFlow<CitySearch> = search.asStateFlow()
    private var searchJob: Job? = null

    val state: StateFlow<WidgetsState?> = combine(appearance.settings, weather.settings, youTube.choice, ::WidgetsState)
        .stateIn(viewModelScope, WhileUiSubscribed, null)

    override fun toggleDate() = look { it.copy(showDate = !it.showDate) }

    override fun setClockFormat(format: ClockFormat) = look { it.copy(clockFormat = format) }

    override fun toggleWeather() = weatherChoice { it.copy(show = !it.show) }

    override fun setFahrenheit(fahrenheit: Boolean) = weatherChoice { it.copy(fahrenheit = fahrenheit) }

    override fun toggleNowPlaying() = look { it.copy(showNowPlaying = !it.showNowPlaying) }

    override fun useYouTubeApp(app: YouTubeApp) {
        viewModelScope.launch { youTube.use(app) }
    }

    override fun searchCity(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        search.value = CitySearch.Searching
        searchJob = viewModelScope.launch {
            search.value = try {
                CitySearch.Found(places.search(query.trim()))
            } catch (_: IOException) {
                CitySearch.Failed
            }
        }
    }

    override fun choosePlace(place: Place?) {
        closeSearch()
        weatherChoice { it.copy(place = place) }
    }

    override fun closeSearch() {
        searchJob?.cancel()
        searchJob = null
        search.value = CitySearch.Idle
    }

    private fun look(transform: (HomeAppearance) -> HomeAppearance) {
        viewModelScope.launch { appearance.update(transform) }
    }

    private fun weatherChoice(transform: (WeatherSettings) -> WeatherSettings) {
        viewModelScope.launch { weather.update(transform) }
    }
}
