package dev.lumbina.tv.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lumbina.tv.core.domain.home.BarPlace
import dev.lumbina.tv.core.domain.home.RowSpotlight
import dev.lumbina.tv.core.domain.home.TopBarLook

/** What is set on home itself while the panel steps aside: the top bar's place, or a row's size (by its key). */
@Composable
internal fun Adjusting(key: String, onDone: () -> Unit) {
    if (key == RowSpotlight.TOP_BAR) BarPlacer(onDone) else SizeSlider(key, onDone)
}

/**
 * The top bar's place, set on the home screen itself: the settings' panel is out of the way, the
 * bar stands where it is set, and a card at the bottom says where. ◀ ▶ move the bar at once, OK
 * keeps it there, Back puts it back where it was when this opened.
 */
@Composable
internal fun BarPlacer(onDone: () -> Unit, viewModel: WidgetsSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = state?.appearance?.topBar?.place ?: return
    val before = remember { now }
    val at = PLACES.indexOf(now)
    val move = { by: Int ->
        PLACES.getOrNull(at + by)?.let { place -> viewModel.changeTopBar { it.copy(place = place) } }
    }
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = {
            viewModel.changeTopBar { it.copy(place = before) }
            onDone()
        },
        properties = PopupProperties(focusable = true),
    ) {
        SliderCard(
            title = stringResource(R.string.bar_place_title),
            note = null,
            value = stringResource(now.title),
            at = at,
            usual = PLACES.indexOf(TopBarLook().place),
            count = PLACES.size,
            keys = stringResource(R.string.bar_place_keys),
            onKey = { pressed ->
                when (pressed) {
                    Key.DirectionLeft -> move(-1)
                    Key.DirectionRight -> move(1)
                    Key.DirectionCenter, Key.Enter -> onDone()
                    else -> Unit
                }
            },
        )
    }
}

/** The places as they stand on the screen, so ◀ ▶ move the bar the way they point. */
private val PLACES = listOf(BarPlace.Left, BarPlace.Center, BarPlace.Right)
