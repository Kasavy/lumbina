package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.HomeRowEntry
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.SectionStyle

// The rows list of the home settings page: Favorites pinned first, the other rows in their
// order; moving one is MoveKeys'.

/** A row being moved, and the order to put back if the move is cancelled. */
internal data class MovingRow(val key: String, val before: List<String>)

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
    onCategory: (HomeSection) -> Unit,
    onNew: () -> Unit,
) {
    item { Header(stringResource(R.string.home_rows)) }
    item {
        SettingRow(
            onClick = {},
            leadingContent = {
                RowIcon(R.drawable.ic_lock, stringResource(R.string.home_row_pinned))
            },
            headlineContent = { Text(stringResource(R.string.home_row_favorites)) },
            supportingContent = { Text(stringResource(R.string.home_row_favorites_hint)) },
            dimmed = moving != null,
        )
    }
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
            val focus = Modifier.focusedIf(returning, entry.row)
            RowEntry(entry, position, state, moving, onMoving, commands, onCategory, focus)
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

/** One row below Favorites: a built-in one with its switch, or a category that opens its menu. */
@Suppress("LongParameterList") // the row, the list's state and what the row does
@Composable
private fun RowEntry(
    entry: HomeRowEntry,
    position: Int,
    state: HomeSettingsState,
    moving: MovingRow?,
    onMoving: (MovingRow?) -> Unit,
    commands: HomeSettingsCommands,
    onCategory: (HomeSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val key = entry.row.key
    val category = (entry.row as? HomeRow.Category)?.let { row -> state.categories.firstOrNull { it.id == row.id } }
    val title = category?.name ?: builtInTitle(entry.row)
    val isMoving = moving?.key == key
    MovableRow(
        title = title,
        hint = category?.let { categoryHint(it) } ?: builtInHint(entry.row).orEmpty(),
        isMoving = isMoving,
        position = position,
        dimmed = moving != null && !isMoving,
        modifier = modifier,
        onClick = {
            when {
                isMoving -> onMoving(null)
                category != null -> onCategory(category)
                else -> commands.toggleRow(key)
            }
        },
        onLongClick = { if (moving == null) onMoving(MovingRow(key, state.rows.map { it.row.key })) },
        onMoveKey = { pressed -> moving?.let { moveKey(pressed, it, commands, onMoving) } },
    ) {
        if (category != null) {
            Text(
                stringResource(if (entry.visible) R.string.home_category_shown else R.string.home_category_hidden),
                style = MaterialTheme.typography.bodyMedium,
            )
            RowIcon(R.drawable.ic_chevron_right, contentDescription = null)
        } else {
            Switch(checked = entry.visible, onCheckedChange = null)
        }
    }
}

/** ▲ ▼ move the row, OK puts it down, Back puts the rows back as they were. */
private fun moveKey(key: Key, moving: MovingRow, commands: HomeSettingsCommands, onMoving: (MovingRow?) -> Unit) {
    when (key) {
        Key.DirectionUp -> commands.moveRow(moving.key, -1)

        Key.DirectionDown -> commands.moveRow(moving.key, 1)

        Key.DirectionCenter, Key.Enter -> onMoving(null)

        Key.Back, Key.Escape -> {
            commands.setRowOrder(moving.before)
            onMoving(null)
        }

        else -> Unit
    }
}

/**
 * A row that can be picked up: a handle on the left, and while focused a reminder that holding
 * OK moves it. While [isMoving] it is outlined, and [onMoveKey] gets every key press.
 */
@Suppress("LongParameterList") // one row's look and what it does
@Composable
private fun MovableRow(
    title: String,
    hint: String,
    isMoving: Boolean,
    position: Int,
    dimmed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveKey: (Key) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val keys = remember(isMoving) { MoveKeys() }
    // A moving row keeps itself on screen as it goes past the list's edge.
    val view = remember { BringIntoViewRequester() }
    LaunchedEffect(isMoving, position) { if (isMoving) view.bringIntoView() }
    val accent = MaterialTheme.colorScheme.primary
    SettingRow(
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (isMoving) {
                Icon(painterResource(R.drawable.ic_unfold), contentDescription = null, modifier = Modifier.size(28.dp))
            } else {
                RowIcon(R.drawable.ic_drag_indicator, contentDescription = null)
            }
        },
        headlineContent = { Text(title) },
        // While moving, the keys replace the description, right where the eye is.
        supportingContent = if (isMoving) {
            { Text(stringResource(R.string.move_row_hint)) }
        } else {
            // One line: the hold hint takes room on the focused row, which must not grow.
            hint.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (focused && !isMoving) HoldOkHint(R.drawable.ic_unfold)
                trailing()
            }
        },
        outline = accent.takeIf { isMoving },
        dimmed = dimmed,
        modifier = modifier
            .bringIntoViewRequester(view)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                isMoving && keys.handle(event.type, event.key, event.nativeKeyEvent.repeatCount, onMoveKey)
            },
    )
}

@Composable
private fun RowIcon(icon: Int, contentDescription: String?) {
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
private fun builtInTitle(row: HomeRow): String = stringResource(
    when (row) {
        HomeRow.WatchNext -> R.string.home_row_watch_next
        HomeRow.Frequent -> R.string.home_row_frequent
        HomeRow.Opened -> R.string.home_row_opened
        HomeRow.Recent -> R.string.home_row_recent
        HomeRow.Recommendations -> R.string.home_row_recommendations
        is HomeRow.Category -> R.string.home_categories
    },
)

/** What a built-in row shows; null for a category, which is described by its own content. */
@Composable
private fun builtInHint(row: HomeRow): String? = when (row) {
    HomeRow.WatchNext -> R.string.home_row_watch_next_hint
    HomeRow.Frequent -> R.string.home_row_frequent_hint
    HomeRow.Opened -> R.string.home_row_opened_hint
    HomeRow.Recent -> R.string.home_row_recent_hint
    HomeRow.Recommendations -> R.string.home_row_recommendations_hint
    is HomeRow.Category -> null
}?.let { stringResource(it) }

private val SectionStyle.displayName: Int
    get() = when (this) {
        SectionStyle.Row -> R.string.style_row_name
        SectionStyle.Grid -> R.string.style_grid_name
    }

private const val ICON_ALPHA = 0.7f
