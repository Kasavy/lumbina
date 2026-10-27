package dev.lumbina.tv.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.channels.ChannelRow
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.ui.EdgeScrolling
import dev.lumbina.tv.core.ui.PillShape
import dev.lumbina.tv.core.ui.line
import dev.lumbina.tv.core.ui.opensOnRest
import dev.lumbina.tv.core.ui.rememberOpenOnRest

/**
 * A row clips what it draws, and a focused tile grows and glows past its place. So the row
 * reaches from one edge of the screen to the other, over the grid's side padding, and keeps that
 * padding inside: its first tile still lines up with the titles, and nothing is cut at its side.
 */
internal fun Modifier.bleed(): Modifier = layout { measurable, constraints ->
    val extra = GRID_SIDE.roundToPx()
    val wide = constraints.copy(
        minWidth = constraints.minWidth + 2 * extra,
        maxWidth = constraints.maxWidth + 2 * extra,
    )
    val placeable = measurable.measure(wide)
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/** Room above and below a row's tiles for the focused one to grow. */
internal val ROW_MARGIN = 12.dp

/**
 * A home row of tiles, [bleed]ing to the screen's edges and keeping focus within the row. Coming
 * back from the row above or below, focus goes to the tile it left, as it does on Google TV: the
 * nearest tile would creep the row back to its start.
 */
@Composable
internal fun HomeRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    EdgeScrolling(GRID_SIDE) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = GRID_SIDE, vertical = ROW_MARGIN),
            modifier = modifier.bleed().focusRestorer().stayInRow(),
            content = content,
        )
    }
}

/**
 * A row of tiles on its glass backing (the row's "backing" setting, on by default for Favorites).
 * More tiles than fit scroll inside the backing.
 * Focus comes back to the tile it left, as in [HomeRow].
 */
@Composable
internal fun PlateRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    EdgeScrolling(PLATE_INSET) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            contentPadding = PaddingValues(PLATE_INSET),
            modifier = modifier.fillMaxWidth().glassBacking().focusRestorer().stayInRow(),
            content = content,
        )
    }
}

/** Something laid out in lines (a grid of tiles) on the same glass backing as [PlateRow]. */
@Composable
internal fun Plate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().glassBacking().padding(PLATE_INSET)) { content() }
}

/**
 * Tinted glass: the theme's panel, thin enough for the picture to show, with a light edge; tinted
 * so it still stands out on a bright photo.
 */
@Composable
private fun Modifier.glassBacking(): Modifier {
    val glass = MaterialTheme.colorScheme.surface
    val edge = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(PLATE_CORNER)
    return background(glass.copy(alpha = PLATE_ALPHA), shape).border(1.dp, edge.copy(alpha = PLATE_EDGE_ALPHA), shape)
}

/**
 * The room between the backing's edge and its tiles: enough for the focused tile to grow. Tiles on
 * a backing are narrower by it at either side, so a row still shows as many as its size says.
 */
internal val PLATE_INSET = 16.dp
private val PLATE_CORNER = 24.dp
private const val PLATE_ALPHA = 0.4f
private const val PLATE_EDGE_ALPHA = 0.22f

/**
 * All the channels apps publish as one block: its [title] (none when switched off), a tab per
 * channel ("App · channel") and the chosen channel's programs below, looking as [look] says.
 * Moving onto a tab shows it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun Recommendations(
    rows: List<ChannelRow>,
    look: RowLookUi,
    actions: HomeActions,
    title: String?,
    modifier: Modifier = Modifier,
) {
    val size = look.size
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = rows.firstOrNull { it.key == selectedKey } ?: rows.first()
    // A tab opens once the focus rests on it, so coming up from the programs must land on the open one.
    val selectedTab = remember { FocusRequester() }
    // The tabs start to the right of the heading, so nothing stands over the first programs: ▲
    // from those would pass the tabs by, to the row above.
    val tabs = remember { FocusRequester() }
    val resting = rememberOpenOnRest<String> { selectedKey = it }
    val focus = LocalHomeFocus.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (title != null) SectionTitle(title)
            EdgeScrolling(ROW_MARGIN) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(ROW_MARGIN),
                    modifier = Modifier.focusRequester(tabs).stayInRow().focusRestorer(selectedTab),
                ) {
                    items(rows, key = { it.key }) { row ->
                        FilterChip(
                            selected = row.key == selected.key,
                            onClick = { resting.now(row.key) },
                            modifier = Modifier
                                .homeFocus(focus, FocusKey(HomeRow.Recommendations.key, "$TAB_PREFIX${row.key}"))
                                .then(if (row.key == selected.key) Modifier.focusRequester(selectedTab) else Modifier)
                                .opensOnRest(
                                    resting,
                                    row.key,
                                    isOpen = row.key == selected.key,
                                    leave = Key.DirectionDown,
                                ),
                        ) { Text(if (row.isMix) stringResource(R.string.home_recommendations_mix) else row.title()) }
                    }
                }
            }
        }
        // As tall as the open tab: posters stand taller than banners. Switching, the cards grow or
        // shrink to the new height and the new tab's programs fade in, so what is below moves in one
        // smooth go instead of jumping.
        val height by animateDpAsState(programRowHeight(selected.programs, size), tween(TAB_SWITCH_MS))
        val shown = remember { Animatable(1f) }
        var lastKey by remember { mutableStateOf(selected.key) }
        val switching = selected.key != lastKey
        LaunchedEffect(selected.key) {
            if (selected.key == lastKey) return@LaunchedEffect
            lastKey = selected.key
            shown.snapTo(0f)
            shown.animateTo(1f, tween(TAB_SWITCH_MS))
        }
        // Each tab is scrolled by itself (one scrolled far must not open the next in the middle of its
        // own list), and keeps its place while the block is off the screen.
        val scrolls = rememberSaveableStateHolder()
        scrolls.SaveableStateProvider(selected.key) {
            ProgramRow(
                rowKey = HomeRow.Recommendations.key,
                programs = selected.programs,
                height = height,
                actions = actions,
                // Unseen until the fade starts, not shown whole for the frame before it.
                modifier = Modifier.graphicsLayer { alpha = if (switching) 0f else shown.value },
                above = tabs,
                state = rememberLazyListState(),
                look = look,
            )
        }
    }
}

/**
 * "Hold OK on an app to move, rename or hide it": at the bottom of home, never focused, for the
 * first few openings until the user has used it.
 */
@Composable
internal fun HoldOkHint(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(bottom = 40.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = HINT_ALPHA),
                    PillShape,
                )
                .padding(start = 12.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape),
            ) {
                Text(
                    "OK",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(stringResource(R.string.home_hold_ok_hint), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** "App · channel": a channel's tab, or its row's heading. */
internal fun ChannelRow.title(): String = line(app, channel)

private const val HINT_ALPHA = 0.94f

/** How long switching tabs of recommendations takes: the height and the fade of the new programs. */
private const val TAB_SWITCH_MS = 220
