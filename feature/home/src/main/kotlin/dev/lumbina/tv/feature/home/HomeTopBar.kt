package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.sleep.SLEEP_TIMER_MINUTES
import dev.lumbina.tv.core.domain.text.SearchTarget
import dev.lumbina.tv.core.domain.weather.ShownWeather
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.NameDialog
import dev.lumbina.tv.core.ui.dateText
import dev.lumbina.tv.core.ui.iconRes
import dev.lumbina.tv.core.ui.label
import dev.lumbina.tv.core.ui.rememberMinuteClock
import dev.lumbina.tv.core.ui.temperatureText
import dev.lumbina.tv.core.ui.timeText

@Composable
fun HomeTopBarRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopBarViewModel = hiltViewModel(),
    statusViewModel: StatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by statusViewModel.state.collectAsStateWithLifecycle()
    val language = LocalLocale.current.platformLocale.language
    LaunchedEffect(language) { viewModel.onLanguage(language) }
    HomeTopBar(
        state = state,
        onOpenSettings = onOpenSettings,
        onSleepTimer = viewModel::setSleepTimer,
        onSearch = viewModel::search,
        onSystemSearch = viewModel::openSystemSearch,
        modifier = modifier,
        status = {
            StatusIcons(
                status = status,
                actions = StatusActions(
                    openNotice = statusViewModel::openNotice,
                    clearNotices = statusViewModel::clearNotices,
                ),
            )
        },
    )
}

/**
 * Above the home content: the time with the date and the weather (both only shown, never
 * focused), the status, and the actions (search, sleep timer, settings) as the only buttons.
 */
@Composable
internal fun HomeTopBar(
    state: TopBarState,
    onOpenSettings: () -> Unit,
    onSleepTimer: (Int?) -> Unit,
    onSearch: (String) -> Unit,
    onSystemSearch: () -> Unit,
    modifier: Modifier = Modifier,
    status: @Composable () -> Unit = {},
) {
    var choosingTimer by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            val now = rememberMinuteClock()
            Text(timeText(now, state.clockFormat), style = MaterialTheme.typography.displaySmall)
            if (state.showDate) {
                Text(
                    dateText(now),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Weighted, so the buttons on the right keep their size and this part gives way.
        BarMiddle(state.weather, state.sleepMinutesLeft, Modifier.weight(1f))
        status()
        BarDivider()
        TopBarActions(
            search = state.search,
            onSearch = { if (state.search == SearchTarget.System) onSystemSearch() else searching = true },
            onSleepTimer = { choosingTimer = true },
            onOpenSettings = onOpenSettings,
        )
    }
    if (choosingTimer) {
        SleepTimerDialog(
            running = state.sleepMinutesLeft != null,
            onChoose = onSleepTimer,
            onDismiss = { choosingTimer = false },
        )
    }
    if (searching) {
        NameDialog(
            title = stringResource(R.string.home_search_youtube),
            initial = "",
            saveLabel = stringResource(R.string.home_search_find),
            cancelLabel = stringResource(R.string.name_cancel),
            onSave = {
                searching = false
                if (it.isNotBlank()) onSearch(it.trim())
            },
            onDismiss = { searching = false },
        )
    }
}

@Composable
private fun BarMiddle(weather: ShownWeather?, sleepMinutesLeft: Int?, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // The timer's text is measured first; the weather gets the rest, without the city while
        // the timer runs: squeezed next to it, the city would shrink to a letter or two.
        Box(Modifier.weight(1f)) { weather?.let { WeatherNow(it, showPlace = sleepMinutesLeft == null) } }
        sleepMinutesLeft?.let { minutes ->
            Text(
                text = stringResource(R.string.home_sleep_left, minutes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BarDivider(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA)))
}

@Composable
private fun WeatherNow(shown: ShownWeather, showPlace: Boolean, modifier: Modifier = Modifier) {
    val (report, fahrenheit) = shown
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            painterResource(report.weather.condition.iconRes()),
            contentDescription = report.weather.condition.label(),
            modifier = Modifier.size(34.dp),
        )
        Text(report.weather.temperatureText(fahrenheit), style = MaterialTheme.typography.headlineSmall)
        if (showPlace) {
            Text(
                report.place.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The only buttons of the bar: search (named for where it leads, left out when the device has
 * none), sleep timer, settings.
 */
@Composable
private fun TopBarActions(
    search: SearchTarget?,
    onSearch: () -> Unit,
    onSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalHomeFocus.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (search != null) {
            Button(onClick = onSearch, modifier = Modifier.homeFocus(focus, FocusKey(FocusKey.BAR, "search"))) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(
                        if (search == SearchTarget.YouTube) R.string.home_search_youtube else R.string.home_search,
                    ),
                )
            }
        }
        IconButton(onClick = onSleepTimer, modifier = Modifier.homeFocus(focus, FocusKey(FocusKey.BAR, "sleep"))) {
            Icon(painterResource(R.drawable.ic_bedtime), contentDescription = stringResource(R.string.home_sleep_timer))
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.homeFocus(focus, FocusKey(FocusKey.BAR, "settings"))) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings))
        }
    }
}

@Composable
private fun SleepTimerDialog(running: Boolean, onChoose: (Int?) -> Unit, onDismiss: () -> Unit) {
    val options = SLEEP_TIMER_MINUTES.map { minutes ->
        Choice(stringResource(R.string.home_sleep_in, minutes)) { onChoose(minutes) }
    }
    val cancel = listOfNotNull(
        if (running) Choice(stringResource(R.string.home_sleep_cancel)) { onChoose(null) } else null,
    )
    ChoiceDialog(title = stringResource(R.string.home_sleep_timer), choices = cancel + options, onDismiss = onDismiss)
}

private const val DIVIDER_ALPHA = 0.2f
