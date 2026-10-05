package dev.lumbina.tv.platform.weather

import dev.lumbina.tv.core.domain.weather.Condition
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.Weather
import dev.lumbina.tv.core.domain.weather.WeatherProvider
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MetNoTest {

    private val moscow = Place("Moscow", 55.75, 37.62)

    @Test
    fun `reads the current weather`() = runTest {
        val json = """{"type":"Feature","properties":{"timeseries":[{"time":"2026-09-27T00:00:00Z","data":""" +
            """{"instant":{"details":{"air_temperature":9.9}},""" +
            """"next_1_hours":{"summary":{"symbol_code":"partlycloudy_night"}}}}]}}"""
        val weather = MetNoWeather { json.encodeToByteArray() }.current(moscow)
        assertEquals(Weather(9.9, Condition.PartlyCloudy, isDay = false), weather)
    }

    @Test
    fun `maps symbol codes`() {
        assertEquals(Condition.Thunderstorm, conditionOfMetNo("lightrainshowersandthunder_day"))
        assertEquals(Condition.Showers, conditionOfMetNo("rainshowers_night"))
        assertEquals(Condition.Snow, conditionOfMetNo("heavysnowshowers_day"))
        assertEquals(Condition.Drizzle, conditionOfMetNo("lightrain"))
        assertEquals(Condition.Rain, conditionOfMetNo("sleet"))
        assertEquals(Condition.Clear, conditionOfMetNo("clearsky_polartwilight"))
        assertEquals(Condition.Fog, conditionOfMetNo("fog"))
        assertEquals(Condition.Unknown, conditionOfMetNo(""))
    }

    @Test
    fun `falls back to the next provider and stays with it`() = runTest {
        var blockedCalls = 0
        val blocked = WeatherProvider {
            blockedCalls++
            throw IOException("timeout")
        }
        val working = WeatherProvider { Weather(1.0, Condition.Clear) }
        val weather = FallbackWeather(listOf(blocked, working))
        assertEquals(Weather(1.0, Condition.Clear), weather.current(moscow))
        assertEquals(Weather(1.0, Condition.Clear), weather.current(moscow))
        assertEquals(1, blockedCalls)
    }

    @Test(expected = IOException::class)
    fun `fails when every provider fails`() = runTest {
        FallbackWeather(listOf(WeatherProvider { throw IOException("down") })).current(moscow)
    }
}
