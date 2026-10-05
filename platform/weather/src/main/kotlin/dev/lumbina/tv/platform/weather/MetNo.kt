package dev.lumbina.tv.platform.weather

import dev.lumbina.tv.core.domain.weather.Condition
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.Weather
import dev.lumbina.tv.core.domain.weather.WeatherProvider
import dev.lumbina.tv.core.network.HttpGet
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MetNoAnswer(val properties: Properties) {
    @Serializable
    data class Properties(val timeseries: List<Step>)

    @Serializable
    data class Step(val data: Data)

    @Serializable
    data class Data(val instant: Instant, @SerialName("next_1_hours") val nextHour: Period? = null)

    @Serializable
    data class Instant(val details: Details)

    @Serializable
    data class Details(@SerialName("air_temperature") val temperature: Double)

    @Serializable
    data class Period(val summary: Summary)

    @Serializable
    data class Summary(@SerialName("symbol_code") val symbol: String)
}

/**
 * The Norwegian Meteorological Institute (https://api.met.no): free, no key, but asks for an
 * identifying User-Agent (sent by [HttpGet]). Reachable where Open-Meteo is blocked.
 */
internal class MetNoWeather @Inject constructor(private val http: HttpGet) : WeatherProvider {
    override suspend fun current(place: Place): Weather {
        // met.no asks for at most four decimals, so repeated requests hit its cache.
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f"
            .format(Locale.ROOT, place.latitude, place.longitude)
        val now = decode<MetNoAnswer>(http.text(url)).properties.timeseries.firstOrNull()?.data
            ?: throw IOException("no forecast")
        val symbol = now.nextHour?.summary?.symbol.orEmpty()
        return Weather(now.instant.details.temperature, conditionOfMetNo(symbol), isDay = !symbol.endsWith("_night"))
    }
}

/** Maps a met.no symbol code (e.g. `lightrainshowersandthunder_day`) to a [Condition]. */
internal fun conditionOfMetNo(symbol: String): Condition {
    val base = symbol.substringBefore('_')
    return when {
        base in PLAIN_SYMBOLS -> PLAIN_SYMBOLS.getValue(base)
        "thunder" in base -> Condition.Thunderstorm
        "snow" in base -> Condition.Snow
        "showers" in base -> Condition.Showers
        "rain" in base || "sleet" in base -> Condition.Rain
        else -> Condition.Unknown
    }
}

private val PLAIN_SYMBOLS = mapOf(
    "clearsky" to Condition.Clear,
    "fair" to Condition.PartlyCloudy,
    "partlycloudy" to Condition.PartlyCloudy,
    "cloudy" to Condition.Cloudy,
    "fog" to Condition.Fog,
    "lightrain" to Condition.Drizzle,
)

/**
 * Asks the providers in turn and sticks with the one that answered last, so a service blocked
 * on this network costs one timeout, not one per refresh.
 */
@Singleton
internal class FallbackWeather(private val providers: List<WeatherProvider>) : WeatherProvider {

    // met.no first: see [MetNoWeather].
    @Inject constructor(metNo: MetNoWeather, openMeteo: OpenMeteoWeather) : this(listOf(metNo, openMeteo))

    @Volatile private var preferred = 0

    override suspend fun current(place: Place): Weather {
        var failure: IOException? = null
        for (offset in providers.indices) {
            val index = (preferred + offset) % providers.size
            try {
                return providers[index].current(place).also { preferred = index }
            } catch (e: IOException) {
                failure = e
            }
        }
        throw failure ?: IOException("no weather providers")
    }
}
