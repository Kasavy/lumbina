package dev.lumbina.tv.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.channels.inChannelOrder
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.RecommendationsView
import dev.lumbina.tv.core.domain.home.RowSpotlight
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.domain.home.channelRowKey
import dev.lumbina.tv.core.ui.PivotScrolling
import dev.lumbina.tv.core.ui.paceHeldSteps

/** What the grid is built from: home's state, the channels, and the first apps of "All apps" for the "Apps" row. */
internal class HomeRows(val state: HomeUiState?, val channels: ChannelsUi, val apps: List<App>)

/** The titles of the built-in rows: read once, since the grid's items are not built in composition. */
private class RowTitles(
    val recommendations: String,
    val watchNext: String,
    val frequent: String,
    val opened: String,
    val recent: String,
    val apps: String,
)

/** Read once per language: a new object on each recomposition would rebuild every item of the grid. */
@Composable
private fun rememberRowTitles(): RowTitles {
    val recommendations = stringResource(R.string.home_recommendations)
    val watchNext = stringResource(R.string.home_watch_next)
    val frequent = stringResource(R.string.home_frequent)
    val opened = stringResource(R.string.home_opened)
    val recent = stringResource(R.string.home_recently_installed)
    val apps = stringResource(R.string.home_apps)
    return remember(recommendations, watchNext, frequent, opened, recent, apps) {
        RowTitles(recommendations, watchNext, frequent, opened, recent, apps)
    }
}

/** The one grid all of home is laid out in: the [header], then the rows in the user's order. */
@Composable
@Suppress("LongParameterList")
internal fun HomeGrid(
    shown: HomeRows,
    marks: Marks,
    actions: HomeActions,
    grid: LazyGridState,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = shown.state
    val size = state?.appearance?.tileSizeOf(HomeSection.FAVORITES) ?: TileSize.Small
    val favoritesTitle = stringResource(R.string.home_favorites)
    val titles = rememberRowTitles()
    PivotScrolling {
        LazyVerticalGrid(
            state = grid,
            columns = remember { EvenCells(GRID_UNITS) },
            contentPadding = GRID_PADDING,
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
            modifier = modifier.fillMaxSize().paceHeldSteps(),
        ) {
            fullWidth(RowSpotlight.TOP_BAR) { header() }
            if (state == null) {
                homeLoading(favoritesTitle, size)
                return@LazyVerticalGrid
            }
            state.rows.forEach { row -> homeRow(row, state, shown, titles, marks, actions) }
        }
    }
}

@Suppress("LongParameterList", "CyclomaticComplexMethod")
private fun LazyGridScope.homeRow(
    row: HomeRow,
    state: HomeUiState,
    shown: HomeRows,
    titles: RowTitles,
    marks: Marks,
    actions: HomeActions,
) {
    val look = state.appearance
    val channels = shown.channels
    // A row's heading can be switched off; then the row is its tiles alone.
    val title = { text: String -> text.takeIf { look.showTitleIn(row.key) } }
    when (row) {
        HomeRow.Favorites -> state.content.sections.firstOrNull { it.isFavorites }?.let {
            section(it, row.key, look, marks, actions)
        }

        HomeRow.WatchNext -> if (channels.missingPermission != null) {
            needsAccess(row, titles.watchNext, channels.missingPermission, actions)
        } else if (channels.loaded) {
            programRow(row.key, title(titles.watchNext), channels.watchNext, RowLookUi.of(look, row.key), actions)
        } else {
            programRowLoading(row.key, title(titles.watchNext), look.tileSizeOf(row.key))
        }

        HomeRow.Opened -> appRow(row.key, title(titles.opened), state.opened, look, actions)

        HomeRow.Frequent -> appRow(row.key, title(titles.frequent), state.frequent, look, actions)

        HomeRow.Recent -> appRow(row.key, title(titles.recent), state.recent, look, actions)

        HomeRow.Recommendations -> if (channels.missingPermission != null) {
            needsAccess(row, titles.recommendations, channels.missingPermission, actions)
        } else if (look.recommendationsView == RecommendationsView.PerChannel) {
            // The block's own look is every channel row's.
            val rowLook = RowLookUi.of(look, row.key)
            channels.rows.filterNot { it.isMix }.inChannelOrder(look.channelOrder) { it.key }.forEach { channel ->
                val key = channelRowKey(channel.key)
                programRow(
                    key,
                    channel.title().takeIf {
                        look.showTitleIn(row.key)
                    },
                    channel.programs,
                    rowLook,
                    actions,
                )
            }
        } else if (channels.rows.isNotEmpty()) {
            fullWidth("recommendations") {
                Recommendations(channels.rows, RowLookUi.of(look, row.key), actions, title(titles.recommendations))
            }
        }

        is HomeRow.Category -> state.content.sections.firstOrNull { it.id == row.id }?.let {
            section(it, row.key, look, marks, actions)
        }

        HomeRow.AllApps -> appsRow(title(titles.apps), shown.apps, look, actions)
    }
}

/**
 * Home's grid state: rows up to eight screens ahead and behind are made and measured while home
 * stands idle after it opens, and kept, so a step to another row never makes one inside the key
 * press; on a weak box that is what a step costs. Ahead of time and far: while ▼ is held there is
 * no idle time between frames to make the next row in. A row kept off the screen is composed but
 * not placed, and takes no focus ([HomeFocus.focus]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberHomeGrid(): LazyGridState =
    rememberLazyGridState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = 8f, behindFraction = 8f))
