package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.home.ClockFormat
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.sleep.timerMinutesLeft
import dev.lumbina.tv.core.domain.sleep.withTimer
import dev.lumbina.tv.core.domain.text.Search
import dev.lumbina.tv.core.domain.text.SearchTarget
import dev.lumbina.tv.core.domain.weather.CurrentWeather
import dev.lumbina.tv.core.domain.weather.ShownWeather
import dev.lumbina.tv.core.domain.weather.WeatherSettingsRepository
import dev.lumbina.tv.core.domain.weather.shownWeather
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

/**
 * [weather]: null while unknown or switched off; [weatherAwaited]: it is on and still unknown, so
 * its place is kept for it. [sleepMinutesLeft]: minutes until the sleep
 * timer turns the device off, null with no timer. [search]: null hides the search button.
 */
data class TopBarState(
    val weather: ShownWeather? = null,
    val weatherAwaited: Boolean = false,
    val sleepMinutesLeft: Int? = null,
    val search: SearchTarget? = null,
    val showDate: Boolean = true,
    val clockFormat: ClockFormat = ClockFormat.System,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TopBarViewModel @Inject constructor(
    weather: CurrentWeather,
    weatherSettings: WeatherSettingsRepository,
    appearance: HomeAppearanceRepository,
    installedApps: InstalledApps,
    private val sleep: SleepSettingsRepository,
    private val search: Search,
) : ViewModel() {

    /** Minutes left on the sleep timer, counting down only while one runs. */
    private val sleepMinutesLeft = sleep.settings.flatMapLatest { settings ->
        if (settings.timerEndsAt == null) {
            flowOf<Int?>(null)
        } else {
            flow {
                while (true) {
                    emit(settings.timerMinutesLeft(System.currentTimeMillis()))
                    delay(TICK_MILLIS)
                }
            }
        }
    }.distinctUntilChanged()

    /**
     * Looked up again when apps change (installing an app that can search brings the button), and
     * when the user chooses another place to search.
     */
    private val searchTarget = installedApps.observe()
        .flatMapLatest { search.choice }
        .map { it.chosen }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    /** The UI's language: the place by IP is named in it. */
    private val language = MutableStateFlow(Locale.getDefault().language)

    /**
     * Null until the settings are read: the capsule is drawn from them all at once, not a button
     * and a slot at a time as each arrives. The weather comes numbered: its place is kept only
     * until the first answer, and with no network it is not kept empty for good.
     */
    val state: StateFlow<TopBarState?> = combine(
        language.flatMapLatest { weather.reports(it).withIndex() },
        weatherSettings.settings,
        sleepMinutesLeft,
        searchTarget,
        appearance.settings,
    ) { (answers, report), weatherChoice, minutes, search, look ->
        TopBarState(
            weather = shownWeather(report, weatherChoice),
            weatherAwaited = weatherChoice.show && report == null && answers == 0,
            sleepMinutesLeft = minutes,
            search = search,
            showDate = look.showDate,
            clockFormat = look.clockFormat,
        )
    }.stateIn<TopBarState?>(viewModelScope, WhileUiSubscribed, null)

    /** The language the top bar is shown in. */
    fun onLanguage(tag: String) {
        language.value = tag
    }

    /** Looks for [query] where the user chose to search; [onFailed] when nothing opened. */
    fun search(query: String, onFailed: () -> Unit) {
        viewModelScope.launch { if (!search.search(query)) onFailed() }
    }

    /** Opens the device's own search; false when it did not open. */
    fun openSystemSearch(): Boolean = search.openSystem()

    /** Turns the device off in [minutes]; null cancels the timer. */
    fun setSleepTimer(minutes: Int?) {
        viewModelScope.launch { sleep.update { it.withTimer(minutes, System.currentTimeMillis()) } }
    }

    private companion object {
        const val TICK_MILLIS = 15_000L
    }
}
