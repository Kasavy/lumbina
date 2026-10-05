package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.SectionStyle
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.MenuDialog
import dev.lumbina.tv.core.ui.NameDialog

/** A dialog the home panel is showing; a category is named by id so the dialog shows its live state. */
private sealed interface HomePanelDialog {
    data class CategoryActions(val id: String) : HomePanelDialog

    data class Rename(val category: HomeSection) : HomePanelDialog

    data object NewCategory : HomePanelDialog
}

/**
 * Home screen settings: the rows, Favorites pinned first (OK shows or hides a built-in row or opens
 * a category's menu; holding OK moves a row, see [MoveKeys]), tile size, names under tiles, and
 * hidden apps (select one to show it again).
 */
@Composable
fun HomePanel(state: HomeSettingsState?, commands: HomeSettingsCommands, modifier: Modifier = Modifier) {
    if (state == null) return
    var dialog by remember { mutableStateOf<HomePanelDialog?>(null) }
    var moving by remember { mutableStateOf<MovingRow?>(null) }
    SettingsList(modifier) {
        rowItems(
            state = state,
            moving = moving,
            onMoving = { moving = it },
            commands = commands,
            onCategory = { dialog = HomePanelDialog.CategoryActions(it.id) },
            onNew = { dialog = HomePanelDialog.NewCategory },
        )
        tileItems(state.appearance, commands)
        hiddenItems(state.hidden, commands)
    }
    dialog?.let { shown -> HomePanelDialogs(shown, state, commands, show = { dialog = it }) }
}

private fun LazyListScope.tileItems(appearance: HomeAppearance, commands: HomeSettingsCommands) {
    item { Header(stringResource(R.string.home_tiles)) }
    item {
        ChoiceRow(
            title = stringResource(R.string.home_tile_size),
            options = TileSize.entries.map { size ->
                Choice(stringResource(size.title), chosen = size == appearance.tileSize) { commands.setTileSize(size) }
            },
        )
    }
    item {
        SwitchRow(
            title = stringResource(R.string.home_show_labels),
            hint = null,
            checked = appearance.showLabels,
            onClick = commands::toggleLabels,
        )
    }
}

private fun LazyListScope.hiddenItems(hidden: List<App>, commands: HomeSettingsCommands) {
    item { Header(stringResource(R.string.home_hidden)) }
    if (hidden.isEmpty()) {
        item { Note(stringResource(R.string.home_hidden_empty)) }
        return
    }
    item { Note(stringResource(R.string.home_hidden_hint)) }
    items(hidden, key = { it.id.flatten() }) { app ->
        SettingRow(onClick = { commands.unhide(app) }, headlineContent = { Text(app.label) })
    }
}

@Composable
private fun HomePanelDialogs(
    dialog: HomePanelDialog,
    state: HomeSettingsState,
    commands: HomeSettingsCommands,
    show: (HomePanelDialog?) -> Unit,
) {
    val dismiss = { show(null) }
    when (dialog) {
        is HomePanelDialog.CategoryActions -> {
            // Read live, so the switches follow; a deleted category closes its menu.
            val category = state.categories.firstOrNull { it.id == dialog.id } ?: return dismiss()
            val key = HomeRow.Category(category.id).key
            val visible = state.rows.firstOrNull { it.row.key == key }?.visible ?: true
            CategoryDialog(
                category = category,
                visible = visible,
                onToggleVisible = { commands.toggleRow(key) },
                onRename = { show(HomePanelDialog.Rename(category)) },
                onToggleGrid = { commands.toggleStyle(category) },
                onDelete = {
                    dismiss()
                    commands.removeCategory(category.id)
                },
                onDismiss = dismiss,
            )
        }

        is HomePanelDialog.Rename -> NameDialog(
            title = stringResource(R.string.home_category_rename),
            initial = dialog.category.name,
            saveLabel = stringResource(R.string.name_save),
            cancelLabel = stringResource(R.string.action_cancel),
            onSave = { if (it.isNotBlank()) commands.renameCategory(dialog.category.id, it.trim()) },
            onDismiss = dismiss,
        )

        HomePanelDialog.NewCategory -> NameDialog(
            title = stringResource(R.string.home_category_new),
            initial = stringResource(R.string.home_category_default_name),
            saveLabel = stringResource(R.string.name_save),
            cancelLabel = stringResource(R.string.action_cancel),
            onSave = { if (it.isNotBlank()) commands.addCategory(it.trim()) },
            onDismiss = dismiss,
        )
    }
}

/** A category's menu: its switches stay open, renaming and deleting close it. */
@Suppress("LongParameterList") // one callback per menu entry
@Composable
private fun CategoryDialog(
    category: HomeSection,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onRename: () -> Unit,
    onToggleGrid: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val apps = pluralStringResource(R.plurals.home_category_app_count, category.apps.size, category.apps.size)
    MenuDialog(title = category.name, hint = apps, onDismiss = onDismiss) {
        item {
            SwitchRow(
                title = stringResource(R.string.home_category_show),
                hint = null,
                checked = visible,
                onClick = onToggleVisible,
            )
        }
        item {
            SettingRow(
                onClick = onRename,
                headlineContent = { Text(stringResource(R.string.home_category_rename)) },
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.home_category_grid),
                hint = stringResource(R.string.home_category_grid_hint),
                checked = category.style == SectionStyle.Grid,
                onClick = onToggleGrid,
            )
        }
        item {
            SettingRow(
                onClick = onDelete,
                headlineContent = { Text(stringResource(R.string.home_category_delete)) },
                supportingContent = { Text(stringResource(R.string.home_category_delete_hint)) },
            )
        }
    }
}

private val TileSize.title: Int
    get() = when (this) {
        TileSize.Small -> R.string.tile_small
        TileSize.Medium -> R.string.tile_medium
        TileSize.Large -> R.string.tile_large
    }
