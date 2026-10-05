package dev.lumbina.tv.feature.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.channels.ChannelRow
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.focusGlow
import dev.lumbina.tv.core.ui.highlightBorder
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

/** A home row of tiles, [bleed]ing to the screen's edges and keeping focus within the row. */
@Composable
internal fun HomeRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(horizontal = GRID_SIDE, vertical = ROW_MARGIN),
        modifier = modifier.bleed().stayInRow(),
        content = content,
    )
}

/** The last tile of Favorites: the way to every app. */
@Composable
internal fun AllAppsTile(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        Card(
            onClick = onClick,
            colors = CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TILE_ALPHA),
            ),
            border = highlightBorder(),
            glow = focusGlow(),
            modifier = Modifier.fillMaxWidth().aspectRatio(BANNER_ASPECT),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(painterResource(R.drawable.ic_apps), contentDescription = null, modifier = Modifier.size(36.dp))
                Text(stringResource(R.string.home_all_apps), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            text = pluralStringResource(R.plurals.home_apps_count, count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * All the channels apps publish as one block: a tab per channel ("App · channel") and the chosen
 * channel's programs below. Moving onto a tab shows it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun Recommendations(
    rows: List<ChannelRow>,
    size: TileSize,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = rows.firstOrNull { it.key == selectedKey } ?: rows.first()
    // A tab opens once the focus rests on it, so coming up from the programs must land on the open one.
    val selectedTab = remember { FocusRequester() }
    val resting = rememberOpenOnRest<String> { selectedKey = it }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionTitle(stringResource(R.string.home_recommendations))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(ROW_MARGIN),
                modifier = Modifier.stayInRow().focusRestorer(selectedTab),
            ) {
                items(rows, key = { it.key }) { row ->
                    FilterChip(
                        selected = row.key == selected.key,
                        onClick = { resting.now(row.key) },
                        modifier = Modifier
                            .then(if (row.key == selected.key) Modifier.focusRequester(selectedTab) else Modifier)
                            .opensOnRest(resting, row.key, isOpen = row.key == selected.key, leave = Key.DirectionDown),
                    ) { Text(if (row.isMix) stringResource(R.string.home_recommendations_mix) else row.title()) }
                }
            }
        }
        ProgramRow(
            rowKey = HomeRow.Recommendations.key,
            programs = selected.programs,
            height = programRowHeight(selected.programs, size),
            actions = actions,
        )
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
                    RoundedCornerShape(percent = PILL),
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

private fun ChannelRow.title(): String = "$app · $channel"

private const val HINT_ALPHA = 0.94f

/** Corner radius in percent that makes a pill. */
internal const val PILL = 50
private const val BANNER_ASPECT = 16f / 9f
private const val TILE_ALPHA = 0.85f
