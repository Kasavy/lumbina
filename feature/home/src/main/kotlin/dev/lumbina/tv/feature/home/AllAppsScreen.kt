package dev.lumbina.tv.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import dev.lumbina.tv.core.domain.home.AppsOrder
import dev.lumbina.tv.core.ui.PivotScrolling
import dev.lumbina.tv.core.ui.paceHeldSteps
import kotlinx.coroutines.delay

/**
 * Every app on a screen of its own, over the wallpaper: the heading with the search, the order and
 * the filter, then the tiles. It opens from the tile in Favorites, the last tile of "Apps", the
 * quick menu and a button of the remote. Back first takes a search or a filter off, then closes it:
 * asks to ([onClose]) and, [closing], stays on top until home has taken the focus back ([onFinish]),
 * so home is not seen scrolling to whatever tile got the focus first.
 */
@Composable
internal fun AllAppsScreen(
    state: HomeUiState,
    actions: HomeActions,
    onAppsOrder: (AppsOrder) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closing: Boolean = false,
    onFinish: () -> Unit = onClose,
    undone: Int = 0,
) {
    val chose = rememberSaveable(saver = AppsChoice.Saver) { AppsChoice() }
    val apps = rememberAppsSection(state, chose) ?: return
    // A focus of its own: home's, under it, keeps the tile it was opened from.
    val last = rememberSaveable { mutableStateOf<FocusKey?>(null) }
    val focus = remember { HomeFocus(last) }
    val grid = rememberLazyGridState()
    LaunchedEffect(Unit) {
        val first = apps.firstKey ?: AppsSection.FIND
        // The app last focused here may be gone since (hidden, uninstalled): then the first.
        if (last.value?.let { focus.focusSoon(it) } != true) focus.focusSoon(first)
    }
    AppsFocusOnChange(apps, focus)
    AppsFocusOnNeighbour(apps, focus, undone)
    BackHandler { if (chose.narrowed) chose.showAll() else onClose() }
    val closed by rememberUpdatedState(onFinish)
    val nowClosing by rememberUpdatedState(closing)
    // Home may find nothing to focus: then it goes all the same.
    if (closing) LaunchedEffect(Unit) { delay(CLOSE_WAIT_MS).also { closed() } }
    CompositionLocalProvider(LocalHomeFocus provides focus) {
        PivotScrolling {
            LazyVerticalGrid(
                state = grid,
                columns = remember { EvenCells(GRID_UNITS) },
                contentPadding = GRID_PADDING,
                verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
                modifier = modifier
                    .fillMaxSize()
                    .paceHeldSteps()
                    .then(focus.area)
                    .onFocusChanged { if (nowClosing && !it.hasFocus) closed() },
            ) {
                allApps(apps, chose, actions)
            }
        }
    }
    AppsDialogs(apps, chose, onAppsOrder)
}

/**
 * Whether the "All apps" screen is open over home: from its tiles, and on each new request from
 * outside ([toAllApps] counts them). Home itself going to its first tile ([refocus]) closes it, and
 * so does settings coming over home ([visible] false), which shows home live under its panel.
 */
@Composable
internal fun rememberAllApps(toAllApps: Int, refocus: Int, visible: Boolean): MutableState<Boolean> {
    val allApps = rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(toAllApps) {
        if (toAllApps > asked) {
            asked = toAllApps
            allApps.value = true
        }
    }
    // Saved, so a re-created screen does not take the count it starts with for a new press.
    var seenRefocus by rememberSaveable { mutableIntStateOf(refocus) }
    LaunchedEffect(refocus) {
        if (refocus != seenRefocus) {
            seenRefocus = refocus
            allApps.value = false
        }
    }
    LaunchedEffect(visible) { if (!visible) allApps.value = false }
    return allApps
}

/** How long a closing "All apps" waits for home to take the focus. */
private const val CLOSE_WAIT_MS = 500L

/**
 * When the app with the focus goes from the screen (hidden from its menu, uninstalled), the focus
 * goes to the tile after it, from the last to the one before; with none left, to the search. Else
 * nothing has it, and ▼ would not even reach "Undo". Once [undone], it goes back to the app that
 * came back.
 */
@Composable
private fun AppsFocusOnNeighbour(section: AppsSection, focus: HomeFocus, undone: Int) {
    val keys = remember(section) { (section.top + section.rest).map { it.id.flatten() } }
    var before by remember { mutableStateOf(keys) }
    var gone by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(undone) {
        if (undone == 0) return@LaunchedEffect
        // Once: a later undo of something else must not bring the focus back here.
        gone?.let { focus.focusSoon(FocusKey(AppsSection.ROW, it)) }
        gone = null
    }
    LaunchedEffect(keys) {
        val was = before
        before = keys
        val at = focus.last?.takeIf { it.row == AppsSection.ROW } ?: return@LaunchedEffect
        val place = was.indexOf(at.item)
        if (place < 0 || at.item in keys) return@LaunchedEffect
        gone = at.item
        val near = was.drop(place + 1) + was.take(place).reversed()
        val next = near.firstOrNull { it in keys }?.let { FocusKey(AppsSection.ROW, it) }
        focus.focusSoon(next ?: AppsSection.FIND)
    }
}
