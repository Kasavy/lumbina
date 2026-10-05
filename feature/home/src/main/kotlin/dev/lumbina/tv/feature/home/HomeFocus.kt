package dev.lumbina.tv.feature.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalWindowInfo
import dev.lumbina.tv.core.domain.home.HomeRow
import java.io.Serializable

/**
 * An element that can take focus on home: its [row] ([HomeRow.key], a section's id, or [BAR])
 * and the [item] in it, by what the tile shows rather than where, so a tile that moved is still
 * found.
 */
internal data class FocusKey(val row: String, val item: String) : Serializable {
    companion object {
        /** The top bar's buttons. */
        const val BAR = "bar"
        private const val serialVersionUID = 1L
    }
}

/**
 * The elements laid out on home now, and the one that had focus last ([lastFocused], kept in the
 * screen's saved state), so home can go back to it: back from an app (by Home or Back), from
 * settings or all apps.
 */
internal class HomeFocus(private val lastFocused: MutableState<FocusKey?>) {
    private val attached = mutableMapOf<FocusKey, HomeFocusNode>()

    /** The element that had focus last. */
    val last: FocusKey? get() = lastFocused.value

    /** Whether focus is anywhere inside home now, on a registered element or not. */
    var within = false
        private set

    /** For home's outermost layout, so [within] is known. */
    val area: Modifier = Modifier.onFocusChanged { within = it.hasFocus }

    /** Moves focus to [key]'s element; false if it isn't laid out. */
    fun focus(key: FocusKey): Boolean {
        val node = attached[key] ?: return false
        return runCatching { node.requestFocus() }.getOrDefault(false)
    }

    fun attach(key: FocusKey, node: HomeFocusNode) {
        attached[key] = node
    }

    fun detach(key: FocusKey, node: HomeFocusNode) {
        if (attached[key] === node) attached.remove(key)
    }

    fun focused(key: FocusKey) {
        lastFocused.value = key
    }
}

/** Home's [HomeFocus], for the elements composed inside it (tiles, the top bar's buttons). */
@Suppress("CompositionLocalAllowlist")
internal val LocalHomeFocus = staticCompositionLocalOf<HomeFocus?> { null }

/** Registers this element under [key] with home's focus, if inside home. */
internal fun Modifier.homeFocus(focus: HomeFocus?, key: FocusKey): Modifier =
    if (focus == null) this else then(HomeFocusElement(focus, key))

private data class HomeFocusElement(val focus: HomeFocus, val key: FocusKey) : ModifierNodeElement<HomeFocusNode>() {
    override fun create() = HomeFocusNode(focus, key)

    override fun update(node: HomeFocusNode) = node.update(focus, key)
}

/** Registered with [focus] while laid out; reports when it (or what it holds) takes focus. */
internal class HomeFocusNode(private var focus: HomeFocus, private var key: FocusKey) :
    Modifier.Node(),
    FocusRequesterModifierNode,
    FocusEventModifierNode {

    override fun onAttach() = focus.attach(key, this)

    override fun onDetach() = focus.detach(key, this)

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.hasFocus) focus.focused(key)
    }

    fun update(focus: HomeFocus, key: FocusKey) {
        if (focus === this.focus && key == this.key) return
        if (isAttached) this.focus.detach(this.key, this)
        this.focus = focus
        this.key = key
        if (isAttached) focus.attach(key, this)
    }
}

/**
 * When home is shown ([visible]: it stays composed behind settings and all apps): focus goes to
 * the element that had it last ([HomeFocus.last]), else the first tile of its row, else the first
 * tile. When its window gets focus back (from an app, a dialog) the same, unless focus came back
 * inside home by itself. On each new [refocus] (Home pressed on home, a power-on, a long absence, a
 * restart) it goes to the first tile instead.
 */
@Composable
internal fun RestoreFocus(
    focus: HomeFocus,
    state: HomeUiState?,
    channels: ChannelsUi,
    grid: LazyGridState,
    refocus: Int,
    visible: Boolean = true,
) {
    var handled by rememberSaveable { mutableStateOf<Int?>(null) }
    var shownHere by remember { mutableStateOf(false) }
    val firstKey = state?.let(::firstTileKey)
    val shown by rememberUpdatedState(state to channels)
    // On a cold start Android gives focus to the first button it finds when the window gets
    // focus, which is after the tiles appear; asking before that is overruled.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(firstKey, refocus, windowFocused, visible) {
        if (!visible || !windowFocused || firstKey == null) return@LaunchedEffect
        val toStart = handled != refocus
        if (!toStart && shownHere && focus.within) return@LaunchedEffect
        // The first tile may be scrolled away, and a tile off screen can't take focus.
        if (toStart && handled != null) grid.scrollToItem(0)
        val remembered = focus.last.takeUnless { toStart }
        val inRow = remembered?.let { key -> shown.first?.let { firstKeyIn(key.row, it, shown.second) } }
        val targets = listOfNotNull(remembered, inRow, firstKey).distinct()
        val lastBefore = focus.last
        for (target in targets) {
            // The tile may take a few frames to be laid out (its row is lazy too), so keep trying.
            repeat(FOCUS_ATTEMPTS) {
                withFrameNanos { }
                // The user moved on meanwhile: theirs.
                if (focus.last != lastBefore) return@LaunchedEffect
                if (focus.focus(target)) {
                    handled = refocus
                    shownHere = true
                    return@LaunchedEffect
                }
            }
        }
    }
}

/**
 * Brings [row] back to its start when the tile with focus has become its first: rows ordered by
 * use move the tile just used there, while a lazy row keeps its old first tile in view.
 */
@Composable
internal fun ScrollToFocusedFirst(row: LazyListState, firstKey: FocusKey?) {
    val focus = LocalHomeFocus.current ?: return
    LaunchedEffect(firstKey) {
        if (firstKey != null && focus.last == firstKey && row.firstVisibleItemIndex > 0) row.scrollToItem(0)
    }
}

private fun firstTileKey(state: HomeUiState): FocusKey? {
    val favorites = state.content.sections.firstOrNull() ?: return null
    return FocusKey(favorites.id, favorites.tiles.firstOrNull()?.key ?: ALL_APPS_KEY)
}

/** The key of the first element of [row] as home shows it now; null if the row is gone or empty. */
private fun firstKeyIn(row: String, state: HomeUiState, channels: ChannelsUi): FocusKey? {
    val item = when (row) {
        HomeRow.WatchNext.key -> channels.watchNext.firstOrNull()?.id?.toString()

        HomeRow.Opened.key -> state.opened.firstOrNull()?.id?.flatten()

        HomeRow.Frequent.key -> state.frequent.firstOrNull()?.id?.flatten()

        HomeRow.Recent.key -> state.recent.firstOrNull()?.id?.flatten()

        HomeRow.Recommendations.key -> channels.rows.firstOrNull()?.programs?.firstOrNull()?.id?.toString()

        else -> state.content.sections.firstOrNull { it.id == row }?.let { section ->
            section.tiles.firstOrNull()?.key ?: ALL_APPS_KEY.takeIf { section.isFavorites }
        }
    }
    return item?.let { FocusKey(row, it) }
}

internal const val ALL_APPS_KEY = "all-apps"
private const val FOCUS_ATTEMPTS = 30
