package dev.lumbina.tv.core.domain.weather

import dev.lumbina.tv.core.testing.FakeSettings
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentWeatherTest {

    private val moscow = Place("Moscow", 55.75, 37.62)
    private val paris = Place("Paris", 48.85, 2.35)

    private val repository = FakeSettings(WeatherSettings())

    private var ipLookups = 0
    private val locator = object : PlaceLocator {
        override suspend fun byIp(language: String): Place = moscow.also { ipLookups++ }

        override suspend fun search(query: String) = emptyList<Place>()
    }

    private var failing = false
    private var requests = 0
    private val provider = WeatherProvider { place ->
        requests++
        if (failing) throw IOException("offline")
        Weather(if (place == moscow) 5.0 else 15.0, Condition.Cloudy)
    }

    @Test
    fun `uses the IP place until one is chosen, and keeps the last report through failures`() = runTest {
        val weather = CurrentWeather(repository, locator, provider, refreshMillis = 1_000, retryMillis = 100)
        val reports = mutableListOf<WeatherReport?>()
        backgroundScope.launch { weather.reports("en").collect { reports += it } }
        runCurrent()
        assertNull(reports.first())
        assertEquals(WeatherReport(moscow, Weather(5.0, Condition.Cloudy), placeFromIp = true), reports.last())
        assertEquals(moscow, repository.current.value.found) // kept for the night's dimming

        failing = true
        advanceTimeBy(1_001)
        assertEquals(moscow, reports.last()?.place) // stale but shown
        failing = false

        repository.update { it.copy(place = paris) }
        runCurrent()
        assertEquals(WeatherReport(paris, Weather(15.0, Condition.Cloudy), placeFromIp = false), reports.last())
        assertEquals(2, ipLookups) // at the start and at the refresh that failed; not again for its retry
    }

    @Test
    fun `the place by IP is asked again at each regular refresh`() = runTest {
        var here = moscow
        val moving = object : PlaceLocator {
            override suspend fun byIp(language: String): Place = here

            override suspend fun search(query: String) = emptyList<Place>()
        }
        val weather = CurrentWeather(repository, moving, provider, refreshMillis = 1_000, retryMillis = 100)
        val reports = mutableListOf<WeatherReport?>()
        backgroundScope.launch { weather.reports("en").collect { reports += it } }
        runCurrent()
        assertEquals(moscow, reports.last()?.place)

        here = paris // a VPN switched on while the home screen stays open
        advanceTimeBy(1_001)
        assertEquals(paris, reports.last()?.place)
    }

    @Test
    fun `hidden weather asks nothing of the network until it is shown again`() = runTest {
        repository.update { it.copy(show = false) }
        val weather = CurrentWeather(repository, locator, provider, refreshMillis = 1_000, retryMillis = 100)
        val reports = mutableListOf<WeatherReport?>()
        backgroundScope.launch { weather.reports("en").collect { reports += it } }
        advanceTimeBy(5_000)
        assertEquals(0, ipLookups)
        assertEquals(0, requests)
        assertNull(reports.last())

        repository.update { it.copy(show = true) }
        runCurrent()
        assertEquals(moscow, reports.last()?.place)
        assertEquals(1, requests)
    }

    @Test
    fun `collected anew, starts with the last report instead of nothing`() = runTest {
        val weather = CurrentWeather(repository, locator, provider, refreshMillis = 1_000, retryMillis = 100)
        val first = launch { weather.reports("en").collect { } }
        runCurrent()
        first.cancel()

        failing = true
        val reports = mutableListOf<WeatherReport?>()
        backgroundScope.launch { weather.reports("en").collect { reports += it } }
        runCurrent()
        assertEquals(moscow, reports.first()?.place)

        // Another language names the place differently: that report is not this one.
        val other = mutableListOf<WeatherReport?>()
        backgroundScope.launch { weather.reports("ru").collect { other += it } }
        runCurrent()
        assertNull(other.first())
    }

    @Test
    fun `the place by IP is asked for in the language of the reports`() = runTest {
        val asked = mutableListOf<String>()
        val listening = object : PlaceLocator {
            override suspend fun byIp(language: String): Place = moscow.also { asked += language }

            override suspend fun search(query: String) = emptyList<Place>()
        }
        val weather = CurrentWeather(repository, listening, provider, refreshMillis = 1_000, retryMillis = 100)
        backgroundScope.launch { weather.reports("ru").collect { } }
        runCurrent()
        assertEquals(listOf("ru"), asked)
    }
}
