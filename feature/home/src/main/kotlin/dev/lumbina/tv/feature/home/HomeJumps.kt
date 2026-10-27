package dev.lumbina.tv.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import dev.lumbina.tv.core.domain.home.HomeRow
import kotlinx.coroutines.launch

/**
 * Scrolls until an item of the grid whose key [matches] is laid out, [forward] or back, then puts
 * it at the top. The grid's items are built on the go, so an item's place is only known once it is
 * in view. False if there is no such item that way.
 */
internal suspend fun LazyGridState.bringToTop(forward: Boolean, matches: (Any) -> Boolean): Boolean {
    repeat(MAX_PAGES) {
        val found = layoutInfo.visibleItemsInfo.firstOrNull { matches(it.key) }
        if (found != null) {
            scrollToItem(found.index)
            return true
        }
        val page = layoutInfo.viewportSize.height * PAGE
        if (scrollBy(if (forward) page else -page) == 0f) return false
        withFrameNanos { }
    }
    return false
}

/**
 * Whether the grid's item [key] belongs to the row [id] (a section's id or a built-in row's key):
 * its heading, its row of tiles, its "empty" line, the block of recommendations or, laid out a
 * row per channel, any channel's row; or one tile of a section laid out as a grid, which is all a
 * row without a heading has.
 */
internal fun isOfRow(key: Any, id: String): Boolean = key is String &&
    (
        key == "title:$id" || key == "row:$id" || key == "empty:$id" || key == id || key.startsWith("$id:") ||
            (id == HomeRow.Recommendations.key && CHANNEL_ITEMS.any { key.startsWith(it) })
        )

/** A row's key as the grid's items are keyed: a category by its section's id, a built-in row by its own key. */
internal fun rowId(rowKey: String): String = rowKey.removePrefix(CATEGORY_PREFIX)

/**
 * Brings the row [rowKey] (as the settings name it) to the top of the screen, from wherever home
 * is. A row that shows nothing now (no programs, nothing opened yet) is not on home at all: then
 * home stays where it was. A row in view already, even in part, is brought up from its own start
 * (a grid's first line, the first channel), with no run through the rows above.
 */
internal suspend fun LazyGridState.showRow(rowKey: String) {
    val id = rowId(rowKey)
    val inView = layoutInfo.visibleItemsInfo.firstOrNull { isOfRow(it.key, id) }?.index
    if (inView != null) {
        scrollToItem(rowStart(inView, id))
        return
    }
    val index = firstVisibleItemIndex
    val offset = firstVisibleItemScrollOffset
    scrollToItem(0)
    if (!bringToTop(forward = true) { isOfRow(it, id) }) scrollToItem(index, offset)
}

/** The first item of the row [id] that has the item [from]: looked for a page at a time above it. */
private suspend fun LazyGridState.rowStart(from: Int, id: String): Int {
    scrollToItem(from)
    repeat(MAX_PAGES) {
        withFrameNanos { }
        val before = layoutInfo.visibleItemsInfo.filter {
            it.index < from && !isOfRow(it.key, id)
        }.maxOfOrNull { it.index }
        if (before != null) return before + 1
        if (scrollBy(-layoutInfo.viewportSize.height * PAGE) == 0f) return firstVisibleItemIndex
    }
    return from
}

/**
 * The lit row's heading switched back on comes in at the top of the screen with the row, in the
 * same frame. Left alone, the grid would keep the row where it stands and the heading above the
 * screen, and bringing it down after would show as a jump. A row that could not be brought to the
 * top (one of the last) is brought up again instead: the heading pushed it down.
 */
@Composable
internal fun HeadingKeptInView(grid: LazyGridState, rowKey: String?, titled: Boolean?) {
    val seen = remember(rowKey) { Seen(titled) }
    val scope = rememberCoroutineScope()
    // After composition, so the next measure has the heading among the grid's items already.
    SideEffect {
        if (rowKey != null && titled == true && seen.titled == false) {
            // The item the grid stands on; the row above may still show in the padding over it.
            val index = grid.firstVisibleItemIndex
            val first = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            // The heading takes the row's first place, so the row starting the screen keeps it for it.
            if (first != null && isOfRow(first.key, rowId(rowKey))) {
                grid.requestScrollToItem(index, grid.firstVisibleItemScrollOffset)
            } else {
                scope.launch {
                    withFrameNanos { }
                    grid.showRow(rowKey)
                }
            }
        }
        seen.titled = titled
    }
}

/** Whether the lit row had its heading at the last composition. */
private class Seen(var titled: Boolean?)

private const val CATEGORY_PREFIX = "category:"

/** How the tabs of recommendations are keyed in home's focus. */
internal const val TAB_PREFIX = "tab:"

/** How the items of a channel's own row are keyed: its heading and its row of programs. */
private val CHANNEL_ITEMS = listOf("title:channel:", "row:channel:")

/** Lets Back bring the row [key] to its start, where its [first] element is: see [HomeBack]. */
@Composable
internal fun RowStart(key: String, row: LazyListState, first: FocusKey? = null) {
    val focus = LocalHomeFocus.current ?: return
    DisposableEffect(focus, key, row, first) {
        focus.rows[key] = row
        if (first != null) focus.rowStarts[key] = first
        onDispose {
            if (focus.rows[key] === row) {
                focus.rows.remove(key)
                focus.rowStarts.remove(key)
            }
        }
    }
}

/**
 * Back on home, one rule everywhere: from a tile that is not the first of its row, to the first;
 * from the first, to the top of the screen, onto the first tile of the first row; there, nothing.
 * So the way up needs no Home key.
 */
@Composable
internal fun HomeBack(enabled: Boolean, grid: LazyGridState, focus: HomeFocus, shown: HomeRows) {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(shown)
    BackHandler(enabled) {
        scope.launch { back(grid, focus, latest) }
    }
}

private suspend fun back(grid: LazyGridState, focus: HomeFocus, shown: HomeRows) {
    val state = shown.state ?: return
    val at = focus.last
    // The row itself knows best what it starts with now: a row of tabs shows one tab's programs.
    val first = at?.let { focus.rowStarts[it.row] ?: firstKeyIn(it.row, state, shown.channels, shown.apps) }
    // A tab of recommendations stands above its programs: the row's start already, not below it.
    val atStart = first == at || at?.item?.startsWith(TAB_PREFIX) == true
    if (at == null || first == null || atStart) {
        grid.scrollToItem(0)
        firstTileKey(state)?.let { focus.focusSoon(it) }
        return
    }
    focus.rows[at.row]?.scrollToItem(0)
    withFrameNanos { }
    if (focus.focus(first)) return
    // A grid taller than the screen: its first tile is above.
    grid.bringToTop(forward = false) { isOfRow(it, at.row) }
    focus.focusSoon(first)
}

/** Of the screen's height: a page overlaps the one before, so nothing is skipped. */
private const val PAGE = 0.8f
private const val MAX_PAGES = 60
