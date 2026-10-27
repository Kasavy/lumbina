package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.lumbina.tv.core.domain.device.InputSource
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.ui.MenuEntry
import dev.lumbina.tv.core.ui.TileMenu
import dev.lumbina.tv.core.ui.TileName

/** An input's tile: its menu, and the rows that have it. */
@Composable
internal fun InputDialogs(
    dialog: HomeDialog.InputDialog,
    state: HomeUiState,
    commands: HomeCommands,
    menu: MenuState,
    startMoving: (Moving) -> Unit,
) {
    when (dialog) {
        is HomeDialog.InputActions -> InputMenu(dialog, state, commands, menu, startMoving)

        is HomeDialog.InputWhere -> OnceTileWhere(
            tile = dialog.input.name,
            layout = state.layout,
            has = { state.layout.hasInput(it, dialog.input.id) },
            set = { sectionId, shown -> commands.setInput(sectionId, dialog.input.id, shown) },
            menu = menu,
            dialog = dialog,
        )
    }
}

/**
 * Holding OK on an input's tile: as the tile that opens all apps has (move, out of this row, the
 * rows that have it), then its name, then the input itself under its caption.
 */
@Composable
private fun InputMenu(
    dialog: HomeDialog.InputActions,
    state: HomeUiState,
    commands: HomeCommands,
    menu: MenuState,
    startMoving: (Moving) -> Unit,
) {
    val section = state.layout.section(dialog.sectionId) ?: return
    val input = dialog.input
    val group = stringResource(R.string.menu_group_input)
    TileMenu(
        title = input.name,
        entries = onceTileEntries(
            section = section,
            layout = state.layout,
            has = { state.layout.hasInput(it, input.id) },
            onMove = { startMoving(Moving(section.id, input.key)) },
            onOut = { commands.removeInput(section.id, input.id, input.name) },
            onWhere = { menu.show(HomeDialog.InputWhere(input)) },
        ) + listOf(
            MenuEntry(stringResource(R.string.menu_rename)) { menu.show(HomeDialog.RenameInput(input)) },
            MenuEntry(stringResource(R.string.menu_open), group = group) { commands.openInput(input.id, input.name) },
        ),
        onDismiss = { menu.done(dialog) },
        onBack = menu::back,
        subtitle = stringResource(R.string.input_hint),
        focused = menu.cameBackTo,
        onChoose = menu::chose,
    )
}

/** The device's inputs under their caption, each ticked if [section] has its tile; OK puts it in or takes it out. */
internal fun LazyGridScope.inputItems(
    state: HomeUiState,
    section: HomeSection,
    commands: HomeCommands,
    first: Modifier = Modifier,
) {
    if (state.inputs.isEmpty()) return
    group(R.string.pick_apps_inputs)
    items(state.inputs, key = { input -> HomeItem.Input(input.id).key }) { input: InputSource ->
        val inIt = HomeItem.Input(input.id) in section.items
        Box(if (input == state.inputs.first()) first else Modifier) {
            InputTile(
                onClick = { commands.setInput(section.id, input.id, !inIt) },
                label = state.layout.nameOf(input),
                name = TileName.None,
            )
            TickBadge(inIt, Modifier.align(Alignment.TopEnd).offset(x = BADGE_OUT, y = -BADGE_OUT))
        }
    }
}
