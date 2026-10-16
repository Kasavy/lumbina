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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UnplacedAwareModifierNode
import androidx.compose.ui.platform.LocalWindowInfo
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.channelRowKey
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

    /**
     * Moves focus to [key]'s element; false if it isn't on the screen. An element made ahead of
     * time, in a row home keeps ready off the screen, is composed but not placed: focus would take
     * it all the same, out of sight, and nothing would scroll to it.
     */
    fun focus(key: FocusKey): Boolean {
        val node = attached[key]?.takeIf { it.placed } ?: return false
        return runCatching { node.requestFocus() }.getOrDefault(false)
    }

    /** As [focus], for an element about to be laid out: tries for a few frames. */
    suspend fun focusSoon(key: FocusKey): Boolean {
        repeat(FOCUS_ATTEMPTS) {
            if (focus(key)) return true
            withFrameNanos { }
        }
        return false
    }

    /** The rows that scroll sideways, by row, while laid out. */
    val rows = mutableMapOf<String, LazyListState>()

    /** The first element of each such row as it shows now (the open tab's, for a row with tabs). */
    val rowStarts = mutableMapOf<String, FocusKey>()

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

/** Registered with [focus] while composed; reports when it (or what it holds) takes focus. */
internal class HomeFocusNode(private var focus: HomeFocus, private var key: FocusKey) :
    Modifier.Node(),
    FocusRequesterModifierNode,
    FocusEventModifierNode,
    LayoutAwareModifierNode,
    UnplacedAwareModifierNode {

    /** Whether it is placed on the screen now, not only composed. */
    var placed = false
        private set

    override fun onAttach() = focus.attach(key, this)

    override fun onDetach() {
        placed = false
        focus.detach(key, this)
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        placed = true
    }

    override fun onUnplaced() {
        placed = false
    }

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
    apps: List<App> = emptyList(),
) {
    var handled by rememberSaveable { mutableStateOf<Int?>(null) }
    var shownHere by remember { mutableStateOf(false) }
    // Where home was when a screen came over it (settings, all apps): as that screen goes, the
    // focus falls on whatever comes first, which must not count as the user's; and the settings
    // may have scrolled home to the row they change, away from the tile.
    var left by remember { mutableStateOf<LeftAt?>(null) }
    if (!visible) {
        LaunchedEffect(Unit) {
            if (left == null) left = LeftAt(focus.last, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
        }
    }
    val firstKey = state?.let(::firstTileKey)
    val shown by rememberUpdatedState(Triple(state, channels, apps))
    // On a cold start Android gives focus to the first button it finds when the window gets
    // focus, which is after the tiles appear; asking before that is overruled.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(firstKey, refocus, windowFocused, visible) {
        if (!visible || !windowFocused || firstKey == null) return@LaunchedEffect
        val toStart = handled != refocus
        val back = left
        left = null
        val stillOurs = shownHere && focus.within
        if (!toStart && back == null && stillOurs) return@LaunchedEffect
        grid.scrollForFocus(toStart, wasHandled = handled != null, back)
        val remembered = (back?.key ?: focus.last).takeUnless { toStart }
        if (focus.focusFirstOf(restoreTargets(remembered, shown, firstKey))) {
            handled = refocus
            shownHere = true
        }
    }
}

/**
 * The tile to focus may be scrolled away, and a tile off screen can't take focus ([HomeFocus.focus]): home goes back
 * to its top when it goes [toStart] (not on its very first showing), else to where it was [back].
 */
private suspend fun LazyGridState.scrollForFocus(toStart: Boolean, wasHandled: Boolean, back: LeftAt?) {
    if (toStart) {
        if (wasHandled) scrollToItem(0)
    } else if (back != null) {
        scrollToItem(back.index, back.offset)
    }
}

/** The element with the focus and the grid's scroll when a screen came over home. */
private class LeftAt(val key: FocusKey?, val index: Int, val offset: Int)

/** Where focus goes back to: the [remembered] element, else the first of its row, else [firstKey]. */
private fun restoreTargets(
    remembered: FocusKey?,
    shown: Triple<HomeUiState?, ChannelsUi, List<App>>,
    firstKey: FocusKey,
): List<FocusKey> {
    val (state, channels, apps) = shown
    val inRow = state?.let { remembered?.let { key -> firstKeyIn(key.row, it, channels, apps) } }
    return listOfNotNull(remembered, inRow, firstKey).distinct()
}

/**
 * Focuses the first of [targets] that can take it, waiting a few frames for each to be laid out
 * (its row is lazy too); false if none could, or if the user moved the focus meanwhile: then it is theirs.
 */
private suspend fun HomeFocus.focusFirstOf(targets: List<FocusKey>): Boolean {
    val lastBefore = last
    for (target in targets) {
        repeat(FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (last != lastBefore) return false
            if (focus(target)) return true
        }
    }
    return false
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

/**
 * When the tile that had the focus goes from its row (hidden, taken out, its app uninstalled), the
 * focus goes to the next tile of that row; from the last, to the one before; from a row left
 * empty, to the first tile of home.
 */
@Composable
internal fun FocusOnNeighbour(focus: HomeFocus, shown: HomeRows) {
    val at = focus.last
    val items = remember(shown, at?.row) { at?.let { rowItems(it.row, shown) } }
    var before by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    LaunchedEffect(at?.row, items) {
        val was = before
        before = if (at != null && items != null) at.row to items else null
        val now = items.orEmpty()
        // In the same row as before, and gone from it since.
        val gone = at?.takeIf { was?.first == it.row && it.item !in now }
        val row = was?.second.orEmpty()
        val place = gone?.let { row.indexOf(it.item) } ?: -1
        if (gone == null || place < 0) return@LaunchedEffect
        val near = row.drop(place + 1) + row.take(place).reversed()
        val next = near.firstOrNull { it in now }?.let { FocusKey(gone.row, it) }
        (next ?: shown.state?.let(::firstTileKey))?.let { focus.focusSoon(it) }
    }
}

/** The tiles of the app [row] as home shows them now, by their focus keys; null for any other row. */
private fun rowItems(row: String, shown: HomeRows): List<String>? {
    val state = shown.state ?: return null
    return when (row) {
        AppsSection.ROW -> shown.apps.map { it.id.flatten() } + SEE_ALL_KEY
        HomeRow.Opened.key -> state.opened.map { it.id.flatten() }
        HomeRow.Frequent.key -> state.frequent.map { it.id.flatten() }
        HomeRow.Recent.key -> state.recent.map { it.id.flatten() }
        else -> state.content.sections.firstOrNull { it.id == row }?.tiles?.map { it.key }
    }
}

/**
 * The first tile of the first row that has one without asking the channels (those come and go):
 * where home starts. Favorites, wherever it stands, if no row before it has one.
 */
internal fun firstTileKey(state: HomeUiState): FocusKey? {
    val apps by lazy { homeApps(state) }
    val first = state.rows.asSequence()
        .filterNot { it == HomeRow.WatchNext || it == HomeRow.Recommendations }
        .firstNotNullOfOrNull { row -> firstKeyIn(row.key, state, ChannelsUi(), apps) }
    if (first != null) return first
    val favorites = state.content.sections.firstOrNull { it.isFavorites } ?: return null
    return FocusKey(favorites.id, favorites.tiles.firstOrNull()?.key ?: PLUS_KEY)
}

/** The key of the first element of [row] as home shows it now; null if the row is gone or empty. */
internal fun firstKeyIn(row: String, state: HomeUiState, channels: ChannelsUi, apps: List<App>): FocusKey? {
    val item = when (row) {
        AppsSection.ROW -> apps.firstOrNull()?.id?.flatten() ?: SEE_ALL_KEY

        HomeRow.WatchNext.key, HomeRow.Recommendations.key -> firstProgram(row, channels)

        HomeRow.Opened.key -> state.opened.firstOrNull()?.id?.flatten()

        HomeRow.Frequent.key -> state.frequent.firstOrNull()?.id?.flatten()

        HomeRow.Recent.key -> state.recent.firstOrNull()?.id?.flatten()

        // A section, or a channel's own row when recommendations come a row per channel.
        else -> state.content.sections.firstOrNull { it.id == rowId(row) }?.let { section ->
            section.tiles.firstOrNull()?.key ?: PLUS_KEY
        } ?: channels.rows.firstOrNull { channelRowKey(it.key) == row }?.programs?.firstOrNull()?.id?.toString()
    }
    // A section's tiles are registered under its id, not its row's key.
    return item?.let { FocusKey(rowId(row), it) }
}

/** The first card of "Continue watching", or of the recommendations as tabs. */
private fun firstProgram(row: String, channels: ChannelsUi): String? = when {
    channels.missingPermission != null -> "allow"
    row == HomeRow.WatchNext.key -> channels.watchNext.firstOrNull()?.id?.toString()
    else -> channels.rows.firstOrNull()?.programs?.firstOrNull()?.id?.toString()
}

private const val FOCUS_ATTEMPTS = 30
