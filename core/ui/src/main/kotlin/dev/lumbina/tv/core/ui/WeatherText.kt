package dev.lumbina.tv.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.lumbina.tv.core.domain.weather.Condition
import dev.lumbina.tv.core.domain.weather.Weather
import kotlin.math.roundToInt

/** "12°", with a minus sign for frost and no "-0"; in Fahrenheit when asked. */
fun Weather.temperatureText(fahrenheit: Boolean = false): String {
    val degrees = if (fahrenheit) temperatureC * FAHRENHEIT_SCALE + FAHRENHEIT_OFFSET else temperatureC
    val rounded = degrees.roundToInt()
    return "${if (rounded == 0) 0 else rounded}°"
}

private const val FAHRENHEIT_SCALE = 9.0 / 5.0
private const val FAHRENHEIT_OFFSET = 32.0

/** The condition in words, e.g. "Cloudy". */
@Composable
fun Condition.label(): String = stringResource(
    when (this) {
        Condition.Clear -> R.string.weather_clear
        Condition.PartlyCloudy -> R.string.weather_partly_cloudy
        Condition.Cloudy -> R.string.weather_cloudy
        Condition.Fog -> R.string.weather_fog
        Condition.Drizzle -> R.string.weather_drizzle
        Condition.Rain -> R.string.weather_rain
        Condition.Snow -> R.string.weather_snow
        Condition.Showers -> R.string.weather_showers
        Condition.Thunderstorm -> R.string.weather_thunderstorm
        Condition.Unknown -> R.string.weather_unknown
    },
)

/** A line icon for the condition (tint it like text). */
@DrawableRes
fun Condition.iconRes(): Int = when (this) {
    Condition.Clear -> R.drawable.ic_weather_clear
    Condition.PartlyCloudy -> R.drawable.ic_weather_partly
    Condition.Cloudy, Condition.Unknown -> R.drawable.ic_weather_cloudy
    Condition.Fog -> R.drawable.ic_weather_fog
    Condition.Drizzle, Condition.Rain, Condition.Showers -> R.drawable.ic_weather_rain
    Condition.Snow -> R.drawable.ic_weather_snow
    Condition.Thunderstorm -> R.drawable.ic_weather_storm
}
