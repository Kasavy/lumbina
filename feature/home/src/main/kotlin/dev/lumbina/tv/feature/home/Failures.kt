package dev.lumbina.tv.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What home tried to open and could not: said at the bottom of the screen, not only in the log.
 * [NoNotices], [NoNoticesNow] and [NoSearch] are no failures, but are said at the same place and for as long.
 */
internal sealed interface Failed {
    /** The app called [name] did not start. */
    data class App(val name: String) : Failed

    /** The device did not switch to the input called [name]. */
    data class Input(val name: String) : Failed

    /** A program of the app called [app] did not open, and neither did the app. */
    data class Program(val app: String) : Failed

    /** A notification did not open; it stays in the list. */
    data object Notice : Failed

    /** The last notification is gone, and the bell with it. */
    data object NoNotices : Failed

    /** The notifications were asked for by a button and there are none. */
    data object NoNoticesNow : Failed

    /** The search was asked for by a button and the device has nowhere to search. */
    data object NoSearch : Failed

    /** The device's own search did not open. */
    data object SystemSearch : Failed

    /** The app YouTube is searched in did not open. */
    data object YouTubeSearch : Failed
}

/** Keeps the last [Failed] for a few seconds, then lets go of it. */
internal class Failures(private val scope: CoroutineScope) {
    private val last = MutableStateFlow<Failed?>(null)
    private var job: Job? = null

    val state: StateFlow<Failed?> = last.asStateFlow()

    fun say(failed: Failed) {
        job?.cancel()
        job = scope.launch {
            last.value = failed
            delay(FAILED_MILLIS)
            last.value = null
        }
    }
}

/**
 * Of two failures said at the same place, the one that came later: an app that did not start a
 * few seconds ago must not cover the program that did not open just now.
 */
@Composable
internal fun newerOf(first: Failed?, second: Failed?): Failed? {
    var newer by remember { mutableStateOf<Failed?>(null) }
    LaunchedEffect(first) { newer = first ?: second }
    LaunchedEffect(second) { newer = second ?: first }
    // Neither effect has run yet in the composition a failure arrives in.
    return newer.takeIf { it == first || it == second } ?: first ?: second
}

@Composable
internal fun Failed.message(): String = when (this) {
    is Failed.App -> stringResource(R.string.failed_app, name)
    is Failed.Input -> stringResource(R.string.failed_input, name)
    is Failed.Program -> stringResource(R.string.failed_program, app)
    Failed.Notice -> stringResource(R.string.failed_notice)
    Failed.NoNotices -> stringResource(R.string.notices_none)
    Failed.NoNoticesNow -> stringResource(R.string.notices_none_now)
    Failed.NoSearch -> stringResource(R.string.search_none)
    Failed.SystemSearch -> stringResource(R.string.failed_system_search)
    Failed.YouTubeSearch -> stringResource(R.string.failed_youtube_search)
}

private const val FAILED_MILLIS = 6_000L
