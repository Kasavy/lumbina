package dev.lumbina.tv.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What of home must stay in sight over the panels opened on it, in root pixels: its top bar
 * ([bar]) while the bar's own settings are open. Home tells where the bar is as it lays it out.
 */
@Stable
class ScreenMarks {
    var bar: Rect? by mutableStateOf(null)
}

/** One for the activity: home and the settings over it share it. */
@Suppress("CompositionLocalAllowlist")
val LocalScreenMarks = staticCompositionLocalOf { ScreenMarks() }

/**
 * How far down a panel at the right edge begins to keep [area] (root pixels) in sight: just below
 * it where they would overlap (a bar on the right or in the middle), at the top where they only
 * touch (a bar on the left ends about where the panel begins).
 */
@Composable
fun panelTopClearOf(area: Rect?): Dp {
    if (area == null) return 0.dp
    val density = LocalDensity.current
    val panelLeft = LocalWindowInfo.current.containerSize.width -
        with(density) { (PANEL_WIDTH + PANEL_MARGIN - CLEAR_GAP).toPx() }
    return if (area.right <= panelLeft) 0.dp else with(density) { area.bottom.toDp() } + CLEAR_GAP
}

private val CLEAR_GAP = 4.dp
