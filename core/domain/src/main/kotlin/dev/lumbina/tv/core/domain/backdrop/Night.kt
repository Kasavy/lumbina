package dev.lumbina.tv.core.domain.backdrop

import dev.lumbina.tv.core.domain.weather.Place
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** When the sun rises and sets on a day at a place. */
data class SunDay(val rise: Instant, val set: Instant)

/** What a day of the sun is where it does not rise and set: daylight all through, or night all through. */
sealed interface Sun {
    data class Regular(val day: SunDay) : Sun

    data object AlwaysUp : Sun

    data object AlwaysDown : Sun
}

/**
 * Sunrise and sunset on [date] at [place], by the sunrise equation (NOAA's simplification): within
 * a minute or two, which is all the wallpaper needs, and no network.
 */
fun sunOn(date: LocalDate, place: Place): Sun {
    // Days since noon of 1 January 2000, counted from the day's start (UTC).
    val n = ceil(date.toEpochDay() + UNIX_EPOCH_JULIAN - J2000 + LEAP_SECONDS_DAYS)
    val meanNoon = n - place.longitude / FULL_TURN
    val anomaly = (MEAN_ANOMALY_AT_EPOCH + MEAN_ANOMALY_PER_DAY * meanNoon).mod(FULL_TURN)
    val m = anomaly.radians()
    val center = CENTER_1 * sin(m) + CENTER_2 * sin(2 * m) + CENTER_3 * sin(3 * m)
    val longitude = (anomaly + center + HALF_TURN + PERIHELION).mod(FULL_TURN).radians()
    val transit = J2000 + meanNoon + TRANSIT_M * sin(m) - TRANSIT_L * sin(2 * longitude)
    val declination = asin(sin(longitude) * sin(AXIAL_TILT.radians()))
    val latitude = place.latitude.radians()
    val cosHourAngle = (sin(HORIZON.radians()) - sin(latitude) * sin(declination)) / (cos(latitude) * cos(declination))
    return when {
        cosHourAngle > 1 -> Sun.AlwaysDown

        cosHourAngle < -1 -> Sun.AlwaysUp

        else -> {
            val hourAngle = acos(cosHourAngle) * HALF_TURN / PI / FULL_TURN
            Sun.Regular(SunDay(instantOf(transit - hourAngle), instantOf(transit + hourAngle)))
        }
    }
}

/**
 * How far into the night [now] is, 0 by day to 1 at night: it grows over [ramp] after sunset and
 * fades over [ramp] after sunrise, so the wallpaper darkens without a jump. With no [place] known,
 * night runs from [FALLBACK_EVENING] to [FALLBACK_MORNING].
 */
fun nightFraction(now: ZonedDateTime, place: Place?, ramp: Duration = DEFAULT_RAMP): Float {
    val sun = place?.let { sunOn(now.toLocalDate(), it) } ?: Sun.Regular(fallbackDay(now.toLocalDate(), now.zone))
    val day = when (sun) {
        Sun.AlwaysUp -> return 0f
        Sun.AlwaysDown -> return 1f
        is Sun.Regular -> sun.day
    }
    val t = now.toInstant()
    val rampMillis = ramp.toMillis().toFloat()
    return when {
        t < day.rise -> 1f
        t < day.rise + ramp -> 1f - Duration.between(day.rise, t).toMillis() / rampMillis
        t < day.set -> 0f
        else -> (Duration.between(day.set, t).toMillis() / rampMillis).coerceAtMost(1f)
    }
}

private fun fallbackDay(date: LocalDate, zone: ZoneId) = SunDay(
    date.atTime(FALLBACK_MORNING).atZone(zone).toInstant(),
    date.atTime(FALLBACK_EVENING).atZone(zone).toInstant(),
)

private fun instantOf(julian: Double): Instant =
    Instant.ofEpochMilli(((julian - UNIX_EPOCH_JULIAN) * MILLIS_PER_DAY).toLong())

private fun Double.radians() = this * PI / HALF_TURN

private val DEFAULT_RAMP: Duration = Duration.ofMinutes(30)
private val FALLBACK_EVENING: LocalTime = LocalTime.of(22, 0)
private val FALLBACK_MORNING: LocalTime = LocalTime.of(7, 0)

private const val UNIX_EPOCH_JULIAN = 2_440_587.5
private const val J2000 = 2_451_545.0
private const val LEAP_SECONDS_DAYS = 0.0008
private const val FULL_TURN = 360.0
private const val HALF_TURN = 180.0
private const val MEAN_ANOMALY_AT_EPOCH = 357.5291
private const val MEAN_ANOMALY_PER_DAY = 0.98560028
private const val CENTER_1 = 1.9148
private const val CENTER_2 = 0.02
private const val CENTER_3 = 0.0003
private const val PERIHELION = 102.9372
private const val TRANSIT_M = 0.0053
private const val TRANSIT_L = 0.0069
private const val AXIAL_TILT = 23.4397

/** The sun's centre this far under the horizon is sunrise or sunset: refraction and its radius. */
private const val HORIZON = -0.833
private const val MILLIS_PER_DAY = 86_400_000.0
