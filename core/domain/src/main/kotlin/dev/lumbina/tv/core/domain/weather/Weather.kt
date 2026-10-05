package dev.lumbina.tv.core.domain.weather

import dev.lumbina.tv.core.domain.settings.SettingsRepository

/** A place to show the weather for. */
data class Place(val name: String, val latitude: Double, val longitude: Double, val region: String? = null)

/** Broad weather conditions, enough to pick an icon and a word. */
enum class Condition { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Showers, Thunderstorm, Unknown }

data class Weather(val temperatureC: Double, val condition: Condition, val isDay: Boolean = true)

/** What the weather row shows: where, and the latest reading. */
data class WeatherReport(val place: Place, val weather: Weather, val placeFromIp: Boolean)

fun interface WeatherProvider {
    /** @throws java.io.IOException when offline or the service fails. */
    suspend fun current(place: Place): Weather
}

interface PlaceLocator {
    /** Where the device's internet connection comes from, named in [language]; null if that can't be told. */
    suspend fun byIp(language: String): Place?

    /** Places matching a city name, best first. */
    suspend fun search(query: String): List<Place>
}

/** [place] null means "find it by IP"; [show] off hides the weather on home and the screensaver. */
data class WeatherSettings(val place: Place? = null, val show: Boolean = true, val fahrenheit: Boolean = false)

typealias WeatherSettingsRepository = SettingsRepository<WeatherSettings>
