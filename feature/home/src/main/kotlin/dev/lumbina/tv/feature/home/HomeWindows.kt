package dev.lumbina.tv.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.domain.text.SearchTarget
import dev.lumbina.tv.core.ui.NameDialog

/**
 * One of home's windows asked for from outside, by a button of the remote: [what] is one of
 * `LumbinaActions.HOME_WINDOWS`, null when none is; [done] is called once it is closed or could
 * not be shown.
 */
class WindowAsk(val what: String?, val done: () -> Unit)

/**
 * The window a button of the remote asked for, over home wherever it is scrolled to: the search,
 * the sleep timer or the notifications, the same ones the line at home's top opens. What there is
 * nothing to open for is said at the bottom of the screen.
 */
@Suppress("ViewModelForwarding")
@Composable
fun HomeWindows(
    ask: WindowAsk,
    viewModel: TopBarViewModel = hiltViewModel(),
    statusViewModel: StatusViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val what = ask.what ?: return
    val read by viewModel.state.collectAsStateWithLifecycle()
    val readStatus by statusViewModel.state.collectAsStateWithLifecycle()
    val state = read ?: return
    val notices = (readStatus ?: return).notices.orEmpty()
    val search = state.search.takeIf { what == LumbinaActions.HOME_SEARCH && it != SearchTarget.System }
    // The panel was on screen and its last notification went: "no more", as from the bell.
    var noticesShown by remember { mutableStateOf(false) }
    when {
        what == LumbinaActions.HOME_SLEEP_TIMER ->
            SleepTimerDialog(state.sleepMinutesLeft, viewModel::setSleepTimer, ask.done)

        what == LumbinaActions.HOME_NOTICES && notices.isNotEmpty() -> {
            LaunchedEffect(Unit) { noticesShown = true }
            AskedNotices(notices, statusViewModel, homeViewModel, ask.done)
        }

        search != null -> AskedSearch(ask.done) { query ->
            viewModel.search(query) { homeViewModel.say(Failed.YouTubeSearch) }
        }

        // Nothing of Lumbina's to show: the device's own search, or a line saying there is none.
        else -> LaunchedEffect(what) {
            when {
                what == LumbinaActions.HOME_NOTICES ->
                    homeViewModel.say(if (noticesShown) Failed.NoNotices else Failed.NoNoticesNow)

                state.search == null -> homeViewModel.say(Failed.NoSearch)

                !viewModel.openSystemSearch() -> homeViewModel.say(Failed.SystemSearch)
            }
            ask.done()
        }
    }
}

@Suppress("ViewModelForwarding")
@Composable
private fun AskedNotices(
    notices: List<NoticeUi>,
    statusViewModel: StatusViewModel,
    homeViewModel: HomeViewModel,
    onDismiss: () -> Unit,
) {
    NoticesPanel(
        notices = notices,
        actions = StatusActions(
            openNotice = { statusViewModel.openNotice(it).also { ok -> if (!ok) homeViewModel.say(Failed.Notice) } },
            dismissNotice = statusViewModel::dismissNotice,
            muteApp = { homeViewModel.muteNotices(it.packageName, it.app) },
            clearNotices = statusViewModel::clearNotices,
            // Said by the window when it finds the list empty.
            noticesGone = {},
        ),
        onDismiss = onDismiss,
    )
}

@Composable
private fun AskedSearch(onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    NameDialog(
        title = stringResource(R.string.home_search_youtube),
        initial = "",
        saveLabel = stringResource(R.string.home_search_find),
        cancelLabel = stringResource(R.string.name_cancel),
        onSave = { query ->
            onDismiss()
            if (query.isNotBlank()) onSearch(query.trim())
        },
        onDismiss = onDismiss,
        needsText = true,
    )
}
