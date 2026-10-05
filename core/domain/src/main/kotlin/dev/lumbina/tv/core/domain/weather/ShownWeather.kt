package dev.lumbina.tv.core.domain.weather

/** The weather as a screen shows it, in the units chosen. */
data class ShownWeather(val report: WeatherReport, val fahrenheit: Boolean)

/** [report] as [settings] want it shown; null while unknown or switched off. */
fun shownWeather(report: WeatherReport?, settings: WeatherSettings): ShownWeather? =
    report?.takeIf { settings.show }?.let { ShownWeather(it, settings.fahrenheit) }
