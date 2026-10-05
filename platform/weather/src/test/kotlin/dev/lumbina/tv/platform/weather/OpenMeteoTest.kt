package dev.lumbina.tv.platform.weather

import dev.lumbina.tv.core.domain.weather.Condition
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.Weather
import dev.lumbina.tv.core.network.HttpGet
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoTest {

    private fun http(vararg answers: Pair<String, String>) = HttpGet { url ->
        answers.firstOrNull { url.contains(it.first) }?.second?.encodeToByteArray()
            ?: throw IOException("no answer for $url")
    }

    @Test
    fun `reads the current weather`() = runTest {
        val json = """{"latitude":55.75,"current":""" +
            """{"time":"2026-09-27T02:00","temperature_2m":7.4,"weather_code":61,"is_day":0}}"""
        val weather = OpenMeteoWeather(http("api.open-meteo.com" to json)).current(Place("Moscow", 55.75, 37.62))
        assertEquals(Weather(7.4, Condition.Rain, isDay = false), weather)
    }

    @Test
    fun `WMO codes map to conditions`() {
        assertEquals(Condition.Clear, conditionOfWmo(0))
        assertEquals(Condition.Snow, conditionOfWmo(73))
        assertEquals(Condition.Thunderstorm, conditionOfWmo(95))
        assertEquals(Condition.Unknown, conditionOfWmo(42))
    }

    @Test
    fun `finds the place by IP, falling back to the second service`() = runTest {
        val ipWho = """{"success":true,"city":"Kazan","region":"Tatarstan","latitude":55.79,"longitude":49.12}"""
        assertEquals(Place("Kazan", 55.79, 49.12, "Tatarstan"), IpPlaceLocator(http("ipwho.is" to ipWho)).byIp("en"))
        val geoJs = """{"city":"Omsk","region":"Omsk Oblast","latitude":"54.99","longitude":"73.37","ip":"1.2.3.4"}"""
        assertEquals(Place("Omsk", 54.99, 73.37, "Omsk Oblast"), IpPlaceLocator(http("geojs.io" to geoJs)).byIp("en"))
        assertNull(IpPlaceLocator(http()).byIp("en"))
    }

    @Test
    fun `the request for the place by IP carries the language it was asked in`() = runTest {
        val asked = mutableListOf<String>()
        val recording = HttpGet { url ->
            asked += url
            """{"success":true,"city":"Казань","latitude":55.79,"longitude":49.12}""".encodeToByteArray()
        }
        assertEquals("Казань", IpPlaceLocator(recording).byIp("ru")?.name)
        assertEquals(listOf(ipWhoUrl("ru")), asked)
    }

    @Test
    fun `the place by IP is asked for in the UI language, where the service knows it`() {
        assertEquals("https://ipwho.is/?fields=success,city,region,latitude,longitude&lang=ru", ipWhoUrl("ru"))
        assertEquals("https://ipwho.is/?fields=success,city,region,latitude,longitude", ipWhoUrl("en"))
        assertEquals("https://ipwho.is/?fields=success,city,region,latitude,longitude", ipWhoUrl("uk"))
    }

    @Test
    fun `searches cities`() = runTest {
        val json = """{"results":[{"name":"Москва","latitude":55.75,"longitude":37.62,""" +
            """"country":"Россия","admin1":"Москва"}]}"""
        val places = IpPlaceLocator(http("geocoding-api" to json)).search("москва")
        assertEquals(listOf(Place("Москва", 55.75, 37.62, "Москва, Россия")), places)
        assertEquals(emptyList<Place>(), IpPlaceLocator(http("geocoding-api" to "{}")).search("nowhere"))
    }

    @Test
    fun `looks up Cyrillic names in Russian`() {
        assertEquals("ru", searchLanguage("Казань", uiLanguage = "en"))
        assertEquals("uk", searchLanguage("Київ", uiLanguage = "uk"))
        assertEquals("en", searchLanguage("Kazan", uiLanguage = "en"))
    }

    @Test(expected = IOException::class)
    fun `a malformed answer is an I-O failure`() = runTest {
        OpenMeteoWeather(http("api.open-meteo.com" to "<html>")).current(Place("X", 0.0, 0.0))
    }
}
