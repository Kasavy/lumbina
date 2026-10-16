package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.weather.Place
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NightTest {

    private val moscow = Place("Moscow", 55.7558, 37.6173)
    private val zone = ZoneId.of("Europe/Moscow")

    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDateTime.parse("${date}T$time"), zone)

    private fun minutesOf(instant: java.time.Instant) = instant.atZone(zone).toLocalTime().toSecondOfDay() / 60

    @Test
    fun `sunrise and sunset in Moscow fall where the tables have them`() {
        // 15 October in Moscow: sunrise about 7:00, sunset about 17:31; solar noon at 12:15.
        val day = (sunOn(LocalDate.parse("2026-10-15"), moscow) as Sun.Regular).day
        assertTrue(abs(minutesOf(day.rise) - (7 * 60)) <= 5, "rise ${day.rise.atZone(zone)}")
        assertTrue(abs(minutesOf(day.set) - (17 * 60 + 31)) <= 5, "set ${day.set.atZone(zone)}")
    }

    @Test
    fun `the night comes on over half an hour after sunset and goes over half an hour after sunrise`() {
        assertEquals(0f, nightFraction(at("2026-10-15", "15:00"), moscow))
        assertEquals(1f, nightFraction(at("2026-10-15", "23:00"), moscow))
        assertEquals(1f, nightFraction(at("2026-10-15", "05:00"), moscow))
        val evening = nightFraction(at("2026-10-15", "17:45"), moscow)
        assertTrue(evening in 0.2f..0.8f, "evening $evening")
        val morning = nightFraction(at("2026-10-15", "07:15"), moscow)
        assertTrue(morning in 0.2f..0.8f, "morning $morning")
    }

    @Test
    fun `beyond the polar circles a day can be all light or all night`() {
        val north = Place("Murmansk", 68.97, 33.07)
        assertEquals(Sun.AlwaysUp, sunOn(LocalDate.parse("2026-06-21"), north))
        assertEquals(Sun.AlwaysDown, sunOn(LocalDate.parse("2026-12-21"), north))
    }

    @Test
    fun `with no place known the night keeps its old hours`() {
        assertEquals(0f, nightFraction(at("2026-10-15", "21:00"), null))
        assertEquals(1f, nightFraction(at("2026-10-15", "06:00"), null))
    }
}
