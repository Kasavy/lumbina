package dev.lumbina.tv.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.ui.MenuEntry
import dev.lumbina.tv.core.ui.TICK
import dev.lumbina.tv.core.ui.TileMenu

/*
 * For the tiles a row may have once, no app's and no folder's: the one that opens all apps and an
 * input's. Their menus begin alike, and the rows that have one are ticked alike.
 */

/** What such a tile's menu begins with: move it, take it out of [section], the rows that have it. */
@Composable
internal fun onceTileEntries(
    section: HomeSection,
    layout: HomeLayout,
    has: (sectionId: String) -> Boolean,
    onMove: () -> Unit,
    onOut: () -> Unit,
    onWhere: () -> Unit,
): List<MenuEntry> {
    val rows = layout.sections.filter { has(it.id) }.map { it.title() }
    val out = if (section.isFavorites) {
        stringResource(R.string.menu_out_of_favorites)
    } else {
        stringResource(R.string.menu_out_of, section.name)
    }
    return listOf(
        MenuEntry(stringResource(R.string.menu_move), "◀ ▶", onChoose = onMove),
        MenuEntry(out, onChoose = onOut),
        MenuEntry(stringResource(R.string.menu_where), rows.joinToString(", ").ifEmpty { null }, onChoose = onWhere),
    )
}

/** Every row, ticked where it has such a tile, named [tile]; OK puts it in or takes it out. */
@Composable
internal fun OnceTileWhere(
    tile: String,
    layout: HomeLayout,
    has: (sectionId: String) -> Boolean,
    set: (sectionId: String, shown: Boolean) -> Unit,
    menu: MenuState,
    dialog: HomeDialog,
) {
    TileMenu(
        title = stringResource(R.string.where_heading),
        entries = layout.sections.map { section ->
            val inIt = has(section.id)
            MenuEntry(section.title(), if (inIt) TICK else null, staysOpen = true) { set(section.id, !inIt) }
        },
        onDismiss = { menu.done(dialog) },
        onBack = menu::back,
        subtitle = stringResource(R.string.where_hint),
        path = listOf(tile),
    )
}
