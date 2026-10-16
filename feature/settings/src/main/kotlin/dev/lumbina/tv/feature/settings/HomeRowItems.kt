package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeRowEntry
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.RecommendationsView
import dev.lumbina.tv.core.domain.home.SectionStyle
import dev.lumbina.tv.core.ui.LINE_SEPARATOR

// The rows list of the home settings page: every row in its order, the channels' own rows under
// the recommendations when they come a row per channel; the rows themselves are in MovableRows.kt.

/** A row (or a channel's row) being moved, and the order to put back if the move is cancelled. */
internal data class MovingRow(val key: String, val before: List<String>, val channel: Boolean = false)

/** The category being put back: its row takes the focus the offer to put it back had. */
internal class Returning {
    var id: String? = null
}

@Suppress("LongParameterList") // the row list's state and what its rows do
internal fun LazyListScope.rowItems(
    state: HomeSettingsState,
    moving: MovingRow?,
    onMoving: (MovingRow?) -> Unit,
    returning: Returning,
    commands: HomeSettingsCommands,
    focusOn: String?,
    onFocus: () -> Unit,
    onRow: (String) -> Unit,
    onNew: () -> Unit,
) {
    // A category just removed leaves a row in its place for a while: OK on it puts the category back.
    val removed = state.removed
    val gap = removed?.row?.coerceIn(0, state.rows.size)
    val undo = {
        returning.id = removed?.section?.id
        commands.undoRemove()
    }
    state.rows.forEachIndexed { position, entry ->
        if (removed != null && position == gap) undoItem(removed, undo, commands::offerFocused)
        item(key = entry.row.key) {
            val focus = Modifier.focusedIf(returning, entry.row).focusedOnReturn(focusOn == entry.row.key, onFocus)
            RowEntry(entry, position, state, moving, onMoving, commands, onRow, focus)
        }
        if (entry.row == HomeRow.Recommendations &&
            state.appearance.recommendationsView == RecommendationsView.PerChannel
        ) {
            channelItems(state, moving, onMoving, commands)
        }
    }
    if (removed != null && gap == state.rows.size) undoItem(removed, undo, commands::offerFocused)
    item {
        SettingRow(
            onClick = onNew,
            leadingContent = { RowIcon(R.drawable.ic_add, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.home_category_new)) },
            dimmed = moving != null,
        )
    }
}

private fun LazyListScope.undoItem(removed: RemovedCategory, onUndo: () -> Unit, onFocused: (Boolean) -> Unit) {
    item(key = "removed:${removed.section.id}") {
        // The focus was on the category's row, which is gone: its place takes it, the first time
        // it is shown and not each time the list brings it back on screen.
        val focus = remember { FocusRequester() }
        val first = remember { !removed.shown }
        LaunchedEffect(Unit) { if (first) runCatching { focus.requestFocus() } }
        DisposableEffect(Unit) { onDispose { onFocused(false) } }
        SettingRow(
            onClick = onUndo,
            modifier = Modifier.focusRequester(focus).onFocusChanged { onFocused(it.isFocused) },
            headlineContent = { Text(stringResource(R.string.home_category_removed, removed.section.name)) },
            supportingContent = { Text(stringResource(R.string.home_category_undo)) },
        )
    }
}

/** Takes the focus once if [row] is the category [returning]. */
@Composable
private fun Modifier.focusedIf(returning: Returning, row: HomeRow): Modifier {
    val id = returning.id ?: return this
    if ((row as? HomeRow.Category)?.id != id) return this
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        returning.id = null
        runCatching { focus.requestFocus() }
    }
    return focusRequester(focus)
}

/** One row of home: whether it is shown, and the way into its own page. */
@Suppress("LongParameterList") // the row, the list's state and what the row does
@Composable
private fun RowEntry(
    entry: HomeRowEntry,
    position: Int,
    state: HomeSettingsState,
    moving: MovingRow?,
    onMoving: (MovingRow?) -> Unit,
    commands: HomeSettingsCommands,
    onRow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val key = entry.row.key
    val category = (entry.row as? HomeRow.Category)?.let { row -> state.categories.firstOrNull { it.id == row.id } }
    val title = category?.name ?: builtInTitle(entry.row)
    val isMoving = moving?.key == key && !moving.channel
    MovableRow(
        title = title,
        // Shown or not comes first on the second line, then what the row is: nothing stands at the right.
        hint = listOfNotNull(
            stringResource(if (entry.visible) R.string.home_category_shown else R.string.home_category_hidden),
            category?.let { categoryHint(it) } ?: builtInHint(entry.row, state),
        ).joinToString(LINE_SEPARATOR),
        isMoving = isMoving,
        position = position,
        dimmed = moving != null && !isMoving,
        modifier = modifier,
        onClick = {
            if (isMoving) onMoving(null) else onRow(key)
        },
        onLongClick = { if (moving == null) onMoving(MovingRow(key, state.rows.map { it.row.key })) },
        onMoveKey = { pressed -> moving?.let { moveKey(pressed, it, commands, onMoving) } },
    ) {
        RowIcon(R.drawable.ic_chevron_right, contentDescription = null)
    }
}

@Composable
internal fun RowIcon(icon: Int, contentDescription: String?) {
    Icon(
        painterResource(icon),
        contentDescription = contentDescription,
        tint = LocalContentColor.current.copy(alpha = ICON_ALPHA),
        modifier = Modifier.size(28.dp),
    )
}

/** "Category · in one line · 3 apps". */
@Composable
private fun categoryHint(category: HomeSection): String = stringResource(
    R.string.home_category_kind,
    stringResource(category.style.displayName),
    pluralStringResource(R.plurals.home_category_app_count, category.apps.size, category.apps.size),
)

@Composable
internal fun builtInTitle(row: HomeRow): String = stringResource(
    when (row) {
        HomeRow.Favorites -> R.string.home_row_favorites
        HomeRow.WatchNext -> R.string.home_row_watch_next
        HomeRow.Frequent -> R.string.home_row_frequent
        HomeRow.Opened -> R.string.home_row_opened
        HomeRow.Recent -> R.string.home_row_recent
        HomeRow.Recommendations -> R.string.home_row_recommendations
        HomeRow.AllApps -> R.string.home_row_all_apps
        is HomeRow.Category -> R.string.home_categories
    },
)

/** What a built-in row shows; null for a category, which is described by its own content. */
@Composable
private fun builtInHint(row: HomeRow, state: HomeSettingsState): String? = when (row) {
    HomeRow.Favorites -> R.string.home_row_favorites_hint

    HomeRow.WatchNext -> R.string.home_row_watch_next_hint

    HomeRow.Frequent -> R.string.home_row_frequent_hint

    HomeRow.Opened -> R.string.home_row_opened_hint

    HomeRow.Recent -> R.string.home_row_recent_hint

    HomeRow.Recommendations -> if (state.appearance.recommendationsView == RecommendationsView.PerChannel) {
        R.string.home_row_recommendations_per_channel
    } else {
        R.string.home_row_recommendations_hint
    }

    HomeRow.AllApps -> return pluralStringResource(
        R.plurals.home_row_all_apps_hint,
        HomeRow.AllApps.APPS,
        HomeRow.AllApps.APPS,
    )

    is HomeRow.Category -> null
}?.let { stringResource(it) }

private val SectionStyle.displayName: Int
    get() = when (this) {
        SectionStyle.Row -> R.string.style_row_name
        SectionStyle.Grid -> R.string.style_grid_name
    }

private const val ICON_ALPHA = 0.7f
