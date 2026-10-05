package dev.lumbina.tv.core.domain.weather

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The weather for the chosen place (or the one found by IP), refreshed every [refreshMillis];
 * after a failure it retries sooner ([retryMillis]) and keeps showing the last good report.
 * Emits null until the first report arrives, and while the weather is hidden: then nothing is
 * asked of the network, not even the place by IP. That place is named in the language asked for.
 */
class CurrentWeather(
    private val settings: WeatherSettingsRepository,
    private val locator: PlaceLocator,
    private val provider: WeatherProvider,
    private val refreshMillis: Long,
    private val retryMillis: Long,
) {
    @Inject constructor(settings: WeatherSettingsRepository, locator: PlaceLocator, provider: WeatherProvider) :
        this(settings, locator, provider, DEFAULT_REFRESH_MILLIS, DEFAULT_RETRY_MILLIS)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun reports(language: String): Flow<WeatherReport?> = settings.settings
        .map { it.show to it.place }
        .distinctUntilChanged()
        .flatMapLatest { (show, place) -> if (show) refreshing(place, language) else flowOf(null) }
        .onStart { emit(null) }

    private fun refreshing(chosen: Place?, language: String): Flow<WeatherReport?> = flow {
        var last: WeatherReport? = null
        var ipPlace: Place? = null
        while (true) {
            val next = runCatching {
                val place = chosen ?: ipPlace ?: locator.byIp(language).also { ipPlace = it }
                place?.let { WeatherReport(it, provider.current(it), placeFromIp = chosen == null) }
            }.getOrElse { if (it is IOException) null else throw it }
            if (next != null) {
                last = next
                ipPlace = null // the place by IP holds through retries; each regular refresh asks again
            }
            emit(last)
            delay(if (next != null) refreshMillis else retryMillis)
        }
    }

    private companion object {
        const val DEFAULT_REFRESH_MILLIS = 30 * 60_000L
        const val DEFAULT_RETRY_MILLIS = 2 * 60_000L
    }
}
