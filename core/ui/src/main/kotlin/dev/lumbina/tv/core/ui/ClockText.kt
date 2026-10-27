package dev.lumbina.tv.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import dev.lumbina.tv.core.domain.MILLIS_PER_MINUTE
import dev.lumbina.tv.core.domain.home.BarDate
import dev.lumbina.tv.core.domain.home.ClockFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** The current time in millis, updated at the start of every minute. */
@Composable
fun rememberMinuteClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MILLIS_PER_MINUTE - System.currentTimeMillis() % MILLIS_PER_MINUTE)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** [time] in the chosen 12/24-hour [format]; [ClockFormat.System] follows the device. */
@Composable
fun timeText(time: Long, format: ClockFormat = ClockFormat.System): String {
    if (format == ClockFormat.System) return DateFormat.getTimeFormat(LocalContext.current).format(Date(time))
    val locale = LocalLocale.current.platformLocale
    val pattern = remember(locale, format) {
        DateFormat.getBestDateTimePattern(locale, if (format == ClockFormat.H24) "Hm" else "hm")
    }
    return DateFormat.format(pattern, time).toString()
}

/** [time]'s date in words, in the UI language: "Sunday, September 27". */
@Composable
fun dateText(time: Long): String {
    val locale = LocalLocale.current.platformLocale
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
    return DateFormat.format(pattern, time).toString().asHeading(locale)
}

/**
 * [time]'s date as the top bar writes it, in the UI language: "Sun, Sep 27", "Sep 27" or "Sunday,
 * September 27"; null for [BarDate.None].
 */
@Composable
fun barDateText(time: Long, format: BarDate): String? {
    val skeleton = when (format) {
        BarDate.None -> return null
        BarDate.WeekdayShort -> "EEEdMMM"
        BarDate.DayMonth -> "dMMM"
        BarDate.Long -> "EEEEdMMMM"
    }
    val locale = LocalLocale.current.platformLocale
    val pattern = remember(locale, skeleton) { DateFormat.getBestDateTimePattern(locale, skeleton) }
    return DateFormat.format(pattern, time).toString()
}

/**
 * Starts with a capital: a date standing alone is a line of its own, while Russian and most other
 * languages write the day of the week in lower case.
 */
internal fun String.asHeading(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
