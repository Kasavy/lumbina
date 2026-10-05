package dev.lumbina.tv.core.domain.weather

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShownWeatherTest {

    private val report = WeatherReport(Place("Riga", 0.0, 0.0), Weather(5.0, Condition.Rain), placeFromIp = false)

    @Test
    fun `shown in the chosen units`() {
        assertEquals(ShownWeather(report, true), shownWeather(report, WeatherSettings(fahrenheit = true)))
    }

    @Test
    fun `nothing while unknown or switched off`() {
        assertNull(shownWeather(null, WeatherSettings()))
        assertNull(shownWeather(report, WeatherSettings(show = false)))
    }
}
