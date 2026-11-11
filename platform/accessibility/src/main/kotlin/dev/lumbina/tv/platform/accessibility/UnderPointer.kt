package dev.lumbina.tv.platform.accessibility

/**
 * A tree of elements as the phone touchpad's pointer reads it, at one point of the screen: kept
 * apart from the accessibility classes, so the walk can be tested on a tree of its own.
 */
internal interface PointerTree<N : Any> {
    /** Whether [node] is shown and covers the point. */
    fun hits(node: N): Boolean

    fun childCount(node: N): Int

    fun child(node: N, index: Int): N?

    /** [node] is no longer needed (the accessibility nodes go back to their pool). */
    fun release(node: N) {}
}

/**
 * The innermost element at the point that [accepts], under [node]. Of overlapping ones the later
 * child, which is usually the one drawn on top (a panel over the screen it was opened from); in
 * Views a sibling raised by its elevation is not. [keep]: a node [accepts] took note of, which
 * must not be released.
 */
internal fun <N : Any> PointerTree<N>.deepest(node: N, keep: (N) -> Boolean = { false }, accepts: (N) -> Boolean): N? {
    if (!hits(node)) return null
    for (index in childCount(node) - 1 downTo 0) {
        val child = child(node, index) ?: continue
        deepest(child, keep, accepts)?.let { return it }
        if (!keep(child)) release(child)
    }
    return node.takeIf(accepts)
}

/** What a scroll step goes to: a list along its axis, or one without an axis, to be scrolled on or back. */
internal sealed interface ScrollTarget<N> {
    val node: N

    data class Along<N>(override val node: N) : ScrollTarget<N>

    data class Axisless<N>(override val node: N) : ScrollTarget<N>
}

/**
 * The list a step scrolls, in the topmost of [roots] (windows, topmost first) that has one under
 * the point: in one walk, one that scrolls [along] the step's axis anywhere under the point before
 * one that doesn't say its axis ([axisless]). A row of tiles inside a page scrolled down is passed
 * by for the page; a window under it is not looked at.
 */
internal fun <N : Any> PointerTree<N>.scrollTarget(
    roots: List<N>,
    along: (N) -> Boolean,
    axisless: (N) -> Boolean,
): ScrollTarget<N>? = roots.firstNotNullOfOrNull { root ->
    var plain: N? = null
    val found = deepest(root, keep = { it === plain }) { node ->
        if (plain == null && axisless(node)) plain = node
        along(node)
    }
    found?.let { ScrollTarget.Along(it) } ?: plain?.let { ScrollTarget.Axisless(it) }
}
