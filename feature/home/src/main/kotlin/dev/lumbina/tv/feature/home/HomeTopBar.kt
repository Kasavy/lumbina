package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.device.InputSource
import dev.lumbina.tv.core.domain.home.BarPlace
import dev.lumbina.tv.core.domain.sleep.SLEEP_TIMER_MINUTES
import dev.lumbina.tv.core.domain.status.problem
import dev.lumbina.tv.core.domain.text.SearchTarget
import dev.lumbina.tv.core.domain.weather.ShownWeather
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.LocalScreenMarks
import dev.lumbina.tv.core.ui.NameDialog
import dev.lumbina.tv.core.ui.PillShape
import dev.lumbina.tv.core.ui.barDateText
import dev.lumbina.tv.core.ui.iconRes
import dev.lumbina.tv.core.ui.label
import dev.lumbina.tv.core.ui.rememberMinuteClock
import dev.lumbina.tv.core.ui.temperatureText
import dev.lumbina.tv.core.ui.timeText
import kotlinx.coroutines.launch

@Composable
fun HomeTopBarRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    plates: @Composable () -> Unit = {},
    viewModel: TopBarViewModel = hiltViewModel(),
    statusViewModel: StatusViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val read by viewModel.state.collectAsStateWithLifecycle()
    val readStatus by statusViewModel.state.collectAsStateWithLifecycle()
    // Both or nothing, so the capsule does not appear half-filled. The line keeps its height meanwhile.
    val state = read
    val status = readStatus
    if (state == null || status == null) {
        Spacer(modifier.height(CAPSULE_HEIGHT))
        return
    }
    val language = LocalLocale.current.platformLocale.language
    LaunchedEffect(language) { viewModel.onLanguage(language) }
    val focus = LocalHomeFocus.current
    val scope = rememberCoroutineScope()
    HomeTopBar(
        state = state,
        onOpenSettings = onOpenSettings,
        onSleepTimer = viewModel::setSleepTimer,
        onSearch = { query -> viewModel.search(query) { homeViewModel.say(Failed.YouTubeSearch) } },
        onSystemSearch = { if (!viewModel.openSystemSearch()) homeViewModel.say(Failed.SystemSearch) },
        onInput = { input -> viewModel.openInput(input) { homeViewModel.say(Failed.Input(input.name)) } },
        modifier = modifier,
        leading = { NowPlayingRoute() },
        plates = {
            NetworkPlate(status.network?.problem(), statusViewModel::openNetwork)
            // What the caller adds stands nearest the capsule.
            plates()
        },
        status = {
            if (state.look.notices) {
                StatusIcons(
                    status = status,
                    actions = StatusActions(
                        openNotice = { notice ->
                            statusViewModel.openNotice(notice).also { if (!it) homeViewModel.say(Failed.Notice) }
                        },
                        dismissNotice = statusViewModel::dismissNotice,
                        // Home's: what is muted can be undone from its bar.
                        muteApp = { homeViewModel.muteNotices(it.packageName, it.app) },
                        clearNotices = statusViewModel::clearNotices,
                        // The bell had the focus and is gone: the settings button is what stays in the capsule.
                        noticesGone = {
                            homeViewModel.say(Failed.NoNotices)
                            scope.launch { focus?.focusSoon(FocusKey(FocusKey.BAR, "settings")) }
                        },
                    ),
                )
            }
        },
    )
}

/**
 * The line above the home content. The capsule, at the right, the left or in the middle as the user
 * set it: the weather, the date and the time (only shown, never focused), the [status] and the
 * buttons (search, inputs, sleep timer, settings), in the same order wherever it stands. By it, on the
 * side away from the screen's edge, the [plates], which speak up only when something needs the user.
 * [leading], what plays, at the side the capsule leaves free: the left, or the right with the capsule at the left.
 */
@Composable
internal fun HomeTopBar(
    state: TopBarState,
    onOpenSettings: () -> Unit,
    onSleepTimer: (Int?) -> Unit,
    onSearch: (String) -> Unit,
    onSystemSearch: () -> Unit,
    onInput: (InputSource) -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    plates: @Composable () -> Unit = {},
    status: @Composable () -> Unit = {},
) {
    var choosingTimer by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var choosingInput by remember { mutableStateOf(false) }
    val settings = remember { FocusRequester() }
    // One node for the caller, dialogs and all: a dialog leaves a node of its own where it is called,
    // and in a column or a row that node would take a gap and push the rest.
    Box(modifier) {
        BarLine(state.look.place, leading, plates, Modifier.fillMaxWidth().enteredFromBelowAt(settings)) {
            Capsule(state, status) {
                TopBarActions(
                    state = state,
                    onSearch = { if (state.search == SearchTarget.System) onSystemSearch() else searching = true },
                    onInputs = { choosingInput = true },
                    onSleepTimer = { choosingTimer = true },
                    onOpenSettings = onOpenSettings,
                    settings = settings,
                )
            }
        }
        if (choosingInput) InputsDialog(state.inputs, onInput, onDismiss = { choosingInput = false })
        if (choosingTimer) {
            SleepTimerDialog(
                left = state.sleepMinutesLeft,
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
                needsText = true,
            )
        }
    }
}

/**
 * The line laid out for the capsule's [place]: [leading] takes what room the capsule leaves at its
 * free side, so the two never overlap; the [plates] stand by the capsule, on its side away from the
 * edge. In the middle, room as wide is kept at either side.
 */
@Composable
private fun BarLine(
    place: BarPlace,
    leading: @Composable () -> Unit,
    plates: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    capsule: @Composable () -> Unit,
) {
    // At the left the line is laid out from the right: the capsule keeps the edge, the plates follow it.
    val direction = if (place == BarPlace.Left) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    contentAlignment = if (place == BarPlace.Left) Alignment.CenterEnd else Alignment.CenterStart,
                    modifier = Modifier.weight(1f),
                ) { leading() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    plates()
                    capsule()
                }
            }
            if (place == BarPlace.Center) Spacer(Modifier.weight(1f))
        }
    }
}

/** The capsule: what is only shown, then the [status] and the [actions], which take the focus. */
@Composable
private fun Capsule(
    state: TopBarState,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    val marks = LocalScreenMarks.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // Where the bar is, for the settings that keep their panel clear of it while it is lit.
            .onGloballyPositioned { marks.bar = it.boundsInRoot() }
            .capsule()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        val now = rememberMinuteClock()
        state.weather?.let {
            WeatherNow(it)
            BarDivider()
        }
        // Its place, while the first report is on its way: the capsule does not grow when it comes.
        if (state.weather == null && state.weatherAwaited) {
            Spacer(Modifier.width(WEATHER_WIDTH))
            BarDivider()
        }
        barDateText(now, state.look.date)?.let { date ->
            Text(
                date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(timeText(now, state.clockFormat), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        status()
        actions()
    }
}

/**
 * ▲ from the rows always lands on [target], wherever in the row the focus was: the line's buttons
 * are small and scattered, and "the nearest one" would be a different one each time.
 */
private fun Modifier.enteredFromBelowAt(target: FocusRequester): Modifier = focusProperties {
    onEnter = { if (requestedFocusDirection == FocusDirection.Up) target.requestFocus() }
}.focusGroup()

/** The capsule's look: the wallpaper shows through, and a thin outline keeps it apart from a dark one. */
@Composable
private fun Modifier.capsule(): Modifier {
    val shape = PillShape
    return background(MaterialTheme.colorScheme.surface.copy(alpha = CAPSULE_ALPHA), shape)
        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA), shape)
}

@Composable
private fun BarDivider(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA)))
}

/** The weather now, without the place: that is said in the settings and on the screensaver. */
@Composable
private fun WeatherNow(shown: ShownWeather, modifier: Modifier = Modifier) {
    val (report, fahrenheit) = shown
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        // No narrower than the place kept for it before it came.
        modifier = modifier.widthIn(min = WEATHER_WIDTH),
    ) {
        Icon(
            painterResource(report.weather.condition.iconRes()),
            contentDescription = report.weather.condition.label(),
            modifier = Modifier.size(24.dp),
        )
        Text(report.weather.temperatureText(fahrenheit), style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/**
 * The capsule's buttons, those the user keeps: search (left out also when the device has none), the
 * inputs (where the device has them), the sleep timer, which says how long is left while it runs
 * (then even when the user left it out), and settings, always there and the last on the right.
 */
@Composable
private fun TopBarActions(
    state: TopBarState,
    onSearch: () -> Unit,
    onInputs: () -> Unit,
    onSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    settings: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val search = state.search
    val sleepMinutesLeft = state.sleepMinutesLeft
    val focus = LocalHomeFocus.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (search != null) {
            val label = if (search == SearchTarget.YouTube) R.string.home_search_youtube else R.string.home_search
            IconButton(onClick = onSearch, modifier = Modifier.homeFocus(focus, FocusKey(FocusKey.BAR, "search"))) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(label))
            }
        }
        if (state.look.inputs && state.inputs.isNotEmpty()) {
            IconButton(onClick = onInputs, modifier = Modifier.homeFocus(focus, FocusKey(FocusKey.BAR, "inputs"))) {
                Icon(
                    painterResource(R.drawable.ic_input_hdmi),
                    contentDescription = stringResource(R.string.home_inputs),
                )
            }
        }
        val sleep = Modifier.homeFocus(focus, FocusKey(FocusKey.BAR, "sleep"))
        if (sleepMinutesLeft != null) {
            Button(onClick = onSleepTimer, modifier = sleep) {
                Icon(painterResource(R.drawable.ic_bedtime), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_sleep_minutes, sleepMinutesLeft), maxLines = 1)
            }
        } else if (state.look.sleep) {
            IconButton(onClick = onSleepTimer, modifier = sleep) {
                Icon(
                    painterResource(R.drawable.ic_bedtime),
                    contentDescription = stringResource(R.string.home_sleep_timer),
                )
            }
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.focusRequester(settings).homeFocus(focus, FocusKey(FocusKey.BAR, "settings")),
        ) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings))
        }
    }
}

/** The inputs button's list: OK on one switches to it. */
@Composable
private fun InputsDialog(inputs: List<InputSource>, onInput: (InputSource) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.home_inputs),
        choices = inputs.map { input -> Choice(input.name) { onInput(input) } },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun SleepTimerDialog(left: Int?, onChoose: (Int?) -> Unit, onDismiss: () -> Unit) {
    val options = SLEEP_TIMER_MINUTES.map { minutes ->
        Choice(stringResource(R.string.home_sleep_in, minutes)) { onChoose(minutes) }
    }
    val cancel = listOfNotNull(
        if (left != null) Choice(stringResource(R.string.home_sleep_cancel)) { onChoose(null) } else null,
    )
    ChoiceDialog(
        title = stringResource(R.string.home_sleep_timer),
        choices = cancel + options,
        onDismiss = onDismiss,
        // While it runs, first of all how long is left.
        hint = left?.let { stringResource(R.string.home_sleep_left, it) },
    )
}

private const val DIVIDER_ALPHA = 0.2f

private const val CAPSULE_ALPHA = 0.6f

/** As tall as the capsule with its buttons: what the line takes before there is anything to draw. */
private val CAPSULE_HEIGHT = 52.dp

/** About what the weather takes, an icon and "16°": kept for it while it is awaited. */
private val WEATHER_WIDTH = 68.dp
