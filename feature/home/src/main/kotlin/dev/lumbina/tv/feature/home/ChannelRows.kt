package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.Dp
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.ProgramTile
import dev.lumbina.tv.core.ui.anchorWhenFocused

/** A row of programs under its title; nothing when there are none. */
internal fun LazyGridScope.programRow(
    key: String,
    title: String,
    programs: List<ProgramCard>,
    size: TileSize,
    actions: HomeActions,
) {
    if (programs.isEmpty()) return
    fullWidth("title:$key") { SectionTitle(title) }
    fullWidth("row:$key") { ProgramRow(key, programs, programRowHeight(programs, size), actions) }
}

/**
 * As tall as a banner row: the banner's height (16:9) for any artwork shape. Rows of mostly
 * posters (films, series) are taller, so the posters are not thin strips.
 */
internal fun programRowHeight(programs: List<ProgramCard>, size: TileSize): Dp {
    val bannerHeight = size.rowTileWidth * PROGRAM_HEIGHT_TO_BANNER_WIDTH
    val posters = programs.count { (it.shape?.widthToHeight ?: 1f) < 1f } * 2 > programs.size
    return if (posters) bannerHeight * POSTER_ROW_SCALE else bannerHeight
}

/** [rowKey] names the row in home's focus keys. */
@Composable
internal fun ProgramRow(
    rowKey: String,
    programs: List<ProgramCard>,
    height: Dp,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val focus = LocalHomeFocus.current
    val state = rememberLazyListState()
    ScrollToFocusedFirst(state, programs.firstOrNull()?.let { FocusKey(rowKey, it.id.toString()) })
    HomeRow(modifier, state) {
        items(programs, key = { it.id }) { card ->
            ProgramTile(
                card = card,
                height = height,
                onClick = { actions.openProgram(card) },
                modifier = Modifier.homeFocus(focus, FocusKey(rowKey, card.id.toString())),
            )
        }
    }
}

/** A row of app tiles under its title (e.g. recently installed); nothing when there are none. */
internal fun LazyGridScope.appRow(
    key: String,
    title: String,
    apps: List<App>,
    size: TileSize,
    showLabels: Boolean,
    actions: HomeActions,
) {
    if (apps.isEmpty()) return
    fullWidth("title:$key") { SectionTitle(title) }
    fullWidth("row:$key") {
        val focus = LocalHomeFocus.current
        val state = rememberLazyListState()
        ScrollToFocusedFirst(state, apps.firstOrNull()?.let { FocusKey(key, it.id.flatten()) })
        HomeRow(state = state) {
            items(apps, key = { it.id.flatten() }) { app ->
                AppCard(
                    app = app,
                    onClick = { actions.open(app) },
                    onLongClick = { actions.appMenu(app, Place.All) },
                    banner = actions.banner(app),
                    showLabel = showLabels,
                    modifier = Modifier
                        .width(size.rowTileWidth)
                        .anchorWhenFocused(actions.anchor)
                        .homeFocus(focus, FocusKey(key, app.id.flatten())),
                )
            }
        }
    }
}

/** ◀ ▶ at either end of a row stay in it instead of jumping into another section. */
internal fun Modifier.stayInRow(): Modifier = focusProperties {
    onExit = {
        if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) {
            cancelFocusChange()
        }
    }
}

private const val PROGRAM_HEIGHT_TO_BANNER_WIDTH = 9f / 16f

private const val POSTER_ROW_SCALE = 1.6f
