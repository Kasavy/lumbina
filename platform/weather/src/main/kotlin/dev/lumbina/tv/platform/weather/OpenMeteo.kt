package dev.lumbina.tv.platform.weather

import dev.lumbina.tv.core.domain.weather.Condition
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.Weather
import dev.lumbina.tv.core.domain.weather.WeatherProvider
import dev.lumbina.tv.core.network.HttpGet
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ForecastAnswer(val current: Current) {
    @Serializable
    data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int = 1,
    )
}

/** Open-Meteo: free, no key (https://open-meteo.com). */
internal class OpenMeteoWeather @Inject constructor(private val http: HttpGet) : WeatherProvider {
    override suspend fun current(place: Place): Weather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code,is_day&timezone=auto"
        val current = decode<ForecastAnswer>(http.text(url)).current
        return Weather(current.temperature, conditionOfWmo(current.weatherCode), isDay = current.isDay == 1)
    }
}

/** Maps WMO weather codes (as used by Open-Meteo) to [Condition]. */
@Suppress("MagicNumber") // the numbers are the WMO code table itself
internal fun conditionOfWmo(code: Int): Condition = when (code) {
    0 -> Condition.Clear
    1, 2 -> Condition.PartlyCloudy
    3 -> Condition.Cloudy
    45, 48 -> Condition.Fog
    in 51..57 -> Condition.Drizzle
    in 61..67 -> Condition.Rain
    in 71..77, 85, 86 -> Condition.Snow
    in 80..82 -> Condition.Showers
    in 95..99 -> Condition.Thunderstorm
    else -> Condition.Unknown
}
