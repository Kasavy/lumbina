package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.ClockFormat
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.text.SearchTarget
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.NameDialog

/** Home screen → Widgets. */
@Composable
fun WidgetsPanel(
    state: WidgetsState?,
    citySearch: CitySearch,
    commands: WidgetsCommands,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    val look = state.appearance
    SettingsColumn(modifier) {
        Note(stringResource(R.string.widgets_note))

        ClockSection(look, commands)

        WeatherSection(
            show = state.weather.show,
            fahrenheit = state.weather.fahrenheit,
            place = state.weather.place,
            citySearch = citySearch,
            commands = commands,
        )

        Header(stringResource(R.string.widgets_now_playing))
        SwitchRow(
            title = stringResource(R.string.widgets_show_now_playing),
            hint = stringResource(R.string.widgets_now_playing_hint),
            checked = look.showNowPlaying,
            onClick = commands::toggleNowPlaying,
        )

        MutedNotices(state.muted, commands::setMuted)

        // With one place to search there is nothing to choose.
        state.search.chosen?.takeIf { state.search.targets.size > 1 }?.let { chosen ->
            Header(stringResource(R.string.widgets_search))
            ChoiceRow(
                title = stringResource(R.string.widgets_search_in),
                hint = stringResource(R.string.widgets_search_in_hint),
                options = state.search.targets.map { target ->
                    val label = when (target) {
                        SearchTarget.System -> R.string.widgets_search_system
                        SearchTarget.YouTube -> R.string.widgets_youtube
                    }
                    Choice(stringResource(label), chosen = target == chosen) { commands.searchIn(target) }
                },
            )
        }

        // With one app for YouTube there is nothing to choose.
        state.youTube.chosen?.takeIf { state.youTube.apps.size > 1 }?.let { chosen ->
            Header(stringResource(R.string.widgets_youtube))
            ChoiceRow(
                title = stringResource(R.string.widgets_youtube_app),
                hint = stringResource(R.string.widgets_youtube_app_hint),
                options = state.youTube.apps.map { app ->
                    Choice(app.label, chosen = app == chosen) { commands.useYouTubeApp(app) }
                },
            )
        }
    }
}

@Composable
private fun WeatherSection(
    show: Boolean,
    fahrenheit: Boolean,
    place: Place?,
    citySearch: CitySearch,
    commands: WidgetsCommands,
    modifier: Modifier = Modifier,
) {
    var typingCity by remember { mutableStateOf(false) }
    // The "automatic" row disappears once chosen; focus moves up instead of leaving the panel.
    val cityFocus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Header(stringResource(R.string.weather_header))
        SwitchRow(
            title = stringResource(R.string.widgets_show_weather),
            hint = stringResource(R.string.widgets_weather_hint),
            checked = show,
            onClick = commands::toggleWeather,
        )
        if (!show) return@Column
        SettingRow(
            onClick = { typingCity = true },
            headlineContent = { Text(stringResource(R.string.weather_city)) },
            modifier = Modifier.focusRequester(cityFocus),
            supportingContent = {
                Text(place?.let(::placeLabel) ?: stringResource(R.string.weather_city_auto))
            },
        )
        if (place != null) {
            SettingRow(
                onClick = {
                    cityFocus.requestFocus()
                    commands.choosePlace(null)
                },
                headlineContent = { Text(stringResource(R.string.weather_city_use_auto)) },
            )
        }
        ChoiceRow(
            title = stringResource(R.string.widgets_units),
            options = listOf(
                Choice("°C", chosen = !fahrenheit) { commands.setFahrenheit(false) },
                Choice("°F", chosen = fahrenheit) { commands.setFahrenheit(true) },
            ),
        )
        when (citySearch) {
            CitySearch.Searching -> Note(stringResource(R.string.weather_searching))
            CitySearch.Failed -> Note(stringResource(R.string.weather_search_failed))
            else -> Unit
        }
    }
    if (typingCity) {
        NameDialog(
            title = stringResource(R.string.weather_city_prompt),
            initial = "",
            saveLabel = stringResource(R.string.weather_search),
            cancelLabel = stringResource(R.string.action_cancel),
            onSave = {
                typingCity = false
                commands.searchCity(it)
            },
            onDismiss = { typingCity = false },
            needsText = true,
        )
    }
    if (citySearch is CitySearch.Found) PlaceChoices(citySearch.places, commands)
}

@Composable
private fun PlaceChoices(places: List<Place>, commands: WidgetsCommands) {
    val title = stringResource(if (places.isEmpty()) R.string.weather_nothing_found else R.string.weather_choose_city)
    ChoiceDialog(
        title = title,
        choices = places.map { place -> Choice(placeLabel(place)) { commands.choosePlace(place) } },
        onDismiss = commands::closeSearch,
    )
}

private fun placeLabel(place: Place): String = listOfNotNull(place.name, place.region).joinToString(", ")

private val ClockFormat.title: Int
    get() = when (this) {
        ClockFormat.System -> R.string.widgets_clock_system
        ClockFormat.H24 -> R.string.widgets_clock_24
        ClockFormat.H12 -> R.string.widgets_clock_12
    }

/**
 * The apps whose notifications are muted, each with a switch that shows them again; nothing while
 * none is. Muting is done from the notifications themselves: here it is undone, and an app shown
 * again keeps its row, with the switch off, until the page is left: the focus stands on it.
 */
@Composable
private fun MutedNotices(muted: List<Pair<String, String>>, onMute: (String, Boolean) -> Unit) {
    val listed = remember { muted }
    if (listed.isEmpty()) return
    Header(stringResource(R.string.widgets_muted, muted.size))
    listed.forEach { (packageName, name) ->
        val on = muted.any { it.first == packageName }
        SwitchRow(
            title = name,
            hint = stringResource(R.string.widgets_muted_hint),
            checked = on,
            onClick = { onMute(packageName, !on) },
        )
    }
}

@Composable
private fun ClockSection(look: HomeAppearance, commands: WidgetsCommands) {
    Header(stringResource(R.string.widgets_clock))
    SwitchRow(
        title = stringResource(R.string.widgets_show_date),
        hint = null,
        checked = look.showDate,
        onClick = commands::toggleDate,
    )
    ChoiceRow(
        title = stringResource(R.string.widgets_clock_format),
        options = ClockFormat.entries.map { format ->
            Choice(stringResource(format.title), chosen = format == look.clockFormat) {
                commands.setClockFormat(format)
            }
        },
    )
}
