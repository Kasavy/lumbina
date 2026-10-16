package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.TileNames
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.BANNER_ASPECT
import dev.lumbina.tv.core.ui.ProgramTile
import dev.lumbina.tv.core.ui.TileName
import dev.lumbina.tv.core.ui.anchorWhenFocused

/** How a row looks on home: its tiles' [size], their [name]s, and whether it stands on its backing ([plate]). */
@Immutable
internal data class RowLookUi(val size: TileSize, val name: TileName, val plate: Boolean) {
    companion object {
        fun of(look: HomeAppearance, key: String) =
            RowLookUi(look.tileSizeOf(key), TileName(look.namesIn(key), look.namesPlaceIn(key)), look.plateIn(key))
    }
}

/** A row of programs under its title; nothing when there are none. */
internal fun LazyGridScope.programRow(
    key: String,
    title: String?,
    programs: List<ProgramCard>,
    look: RowLookUi,
    actions: HomeActions,
) {
    if (programs.isEmpty()) return
    if (title != null) fullWidth("title:$key") { SectionTitle(title) }
    fullWidth("row:$key") {
        ProgramRow(key, programs, programRowHeight(programs, look.size), actions, look = look)
    }
}

/**
 * As tall as a banner row: the banner's height (16:9) for any artwork shape. Rows of mostly
 * posters (films, series) are taller, so the posters are not thin strips.
 */
internal fun programRowHeight(programs: List<ProgramCard>, size: TileSize): Dp {
    val bannerHeight = size.rowTileWidth / BANNER_ASPECT
    val posters = programs.count { (it.shape?.widthToHeight ?: 1f) < 1f } * 2 > programs.size
    return if (posters) bannerHeight * POSTER_ROW_SCALE else bannerHeight
}

/**
 * [rowKey] names the row in home's focus keys. ▲ from any program goes [above], where the row
 * has something of its own there that does not stand over every tile. On its backing when [look]
 * says so; the programs' titles as it says.
 */
@Suppress("LongParameterList")
@Composable
internal fun ProgramRow(
    rowKey: String,
    programs: List<ProgramCard>,
    height: Dp,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    above: FocusRequester? = null,
    state: LazyListState = rememberLazyListState(),
    look: RowLookUi? = null,
) {
    val focus = LocalHomeFocus.current
    val first = programs.firstOrNull()?.let { FocusKey(rowKey, it.id.toString()) }
    ScrollToFocusedFirst(state, first)
    RowStart(rowKey, state, first)
    val tiles: LazyListScope.() -> Unit = {
        items(programs, key = { it.id }) { card ->
            ProgramTile(
                card = card,
                height = height,
                onClick = { actions.openProgram(card) },
                name = look?.name ?: PROGRAM_NAMES,
                modifier = Modifier
                    .then(if (above != null) Modifier.focusProperties { up = above } else Modifier)
                    .homeFocus(focus, FocusKey(rowKey, card.id.toString())),
            )
        }
    }
    if (look?.plate == true) PlateRow(modifier, state, tiles) else HomeRow(modifier, state, tiles)
}

/** A row of app tiles under its title (e.g. recently installed); nothing when there are none. */
internal fun LazyGridScope.appRow(
    key: String,
    title: String?,
    apps: List<App>,
    look: HomeAppearance,
    actions: HomeActions,
    trailing: List<Trailing> = emptyList(),
) {
    if (apps.isEmpty()) return
    val row = RowLookUi.of(look, key)
    if (title != null) fullWidth("title:$key") { SectionTitle(title) }
    fullWidth("row:$key") {
        val focus = LocalHomeFocus.current
        val state = rememberLazyListState()
        val tileWidth = row.size.appTileWidth(inset = if (row.plate) PLATE_INSET else 0.dp)
        ScrollToFocusedFirst(state, apps.firstOrNull()?.let { FocusKey(key, it.id.flatten()) })
        RowStart(key, state)
        val tiles: LazyListScope.() -> Unit = {
            items(apps, key = { it.id.flatten() }) { app ->
                AppCard(
                    app = app,
                    onClick = { actions.open(app) },
                    onLongClick = { actions.appMenu(app, Place.All) },
                    banner = actions.banner(app),
                    name = row.name,
                    modifier = Modifier
                        .width(tileWidth)
                        .anchorWhenFocused(actions.anchor)
                        .homeFocus(focus, FocusKey(key, app.id.flatten())),
                )
            }
            items(trailing, key = { it.key }) { tile ->
                tile.content(Modifier.width(tileWidth).homeFocus(focus, FocusKey(key, tile.key)))
            }
        }
        if (row.plate) PlateRow(state = state, content = tiles) else HomeRow(state = state, content = tiles)
    }
}

/** "Apps": the first apps of the "All apps" screen, and a last tile that opens it. */
internal fun LazyGridScope.appsRow(title: String?, apps: List<App>, look: HomeAppearance, actions: HomeActions) {
    val key = HomeRow.AllApps.key
    val name = RowLookUi.of(look, key).name
    val seeAll = Trailing(SEE_ALL_KEY) { modifier ->
        AllAppsTile(
            onClick = actions.openAllApps,
            label = stringResource(R.string.home_see_all),
            name = name,
            modifier = modifier,
        )
    }
    appRow(key, title, apps, look, actions, trailing = listOf(seeAll))
}

/** ◀ ▶ at either end of a row stay in it instead of jumping into another section. */
internal fun Modifier.stayInRow(): Modifier = focusProperties {
    onExit = {
        if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) {
            cancelFocusChange()
        }
    }
}

/** The last tile of "Apps", which opens the "All apps" screen. */
internal const val SEE_ALL_KEY = "see-all"

/** How programs carry their titles where a row says nothing of its own: all of them, under the cards. */
private val PROGRAM_NAMES = TileName(TileNames.All, NamesPlace.Under)

private const val POSTER_ROW_SCALE = 1.6f
