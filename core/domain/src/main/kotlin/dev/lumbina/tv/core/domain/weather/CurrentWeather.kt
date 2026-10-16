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

/**
 * The weather for the chosen place (or the one found by IP), refreshed every [refreshMillis];
 * after a failure it retries sooner ([retryMillis]) and keeps showing the last good report.
 * Emits null until the first report arrives, and while the weather is hidden: then nothing is
 * asked of the network, not even the place by IP. That place is named in the language asked for.
 * Collected anew, it starts with the last report it had, so what shows the weather does not lose
 * it for the time of a request.
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

    /** The last report, with the place chosen and the language it was asked for. */
    @Volatile
    private var known: Pair<Pair<Place?, String>, WeatherReport>? = null

    private fun refreshing(chosen: Place?, language: String): Flow<WeatherReport?> = flow {
        val asked = chosen to language
        var last: WeatherReport? = known?.takeIf { it.first == asked }?.second
        var ipPlace: Place? = null
        emit(last)
        while (true) {
            val next = runCatching {
                val place = chosen ?: ipPlace ?: locator.byIp(language)?.also { found ->
                    ipPlace = found
                    remember(found)
                }
                place?.let { WeatherReport(it, provider.current(it), placeFromIp = chosen == null) }
            }.getOrElse { if (it is IOException) null else throw it }
            if (next != null) {
                last = next
                known = asked to next
                ipPlace = null // the place by IP holds through retries; each regular refresh asks again
            }
            emit(last)
            delay(if (next != null) refreshMillis else retryMillis)
        }
    }

    /** Keeps the place found by IP (its coordinates, not its name in this language) for the night's dimming. */
    private suspend fun remember(found: Place) {
        settings.update { current ->
            val same = current.found?.let { it.latitude == found.latitude && it.longitude == found.longitude } == true
            if (same) current else current.copy(found = found)
        }
    }

    private companion object {
        const val DEFAULT_REFRESH_MILLIS = 30 * 60_000L
        const val DEFAULT_RETRY_MILLIS = 2 * 60_000L
    }
}
