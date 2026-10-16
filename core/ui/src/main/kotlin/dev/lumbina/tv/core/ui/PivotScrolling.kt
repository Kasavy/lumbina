package dev.lumbina.tv.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * TV-style scrolling for everything inside: the focused item settles about a third of the way
 * into the list (as on Google TV) instead of at the very edge, so a row's title and the row
 * above stay in view.
 */
@OptIn(ExperimentalFoundationApi::class) // LocalBringIntoViewSpec; the TV-recommended way to set this
@Composable
fun PivotScrolling(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides PivotSpec, content = content)
}

/**
 * Scrolling for a row inside [PivotScrolling]: it moves only when the focused item would cross an
 * edge ([margin] in from the side), and just enough to show it whole: focus coming into a row from
 * the one above leaves the row where it is, not slid to put that tile a third of the way in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EdgeScrolling(margin: Dp, content: @Composable () -> Unit) {
    val inset = with(LocalDensity.current) { margin.toPx() }
    val spec = remember(inset) { EdgeSpec(inset) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

internal class EdgeSpec(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = margin
        val end = containerSize - margin
        return when {
            offset < start || size > end - start -> offset - start
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

private object PivotSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val pivot = containerSize * PIVOT
        // An item that doesn't fit below the pivot is aligned to the far edge instead.
        val leadingEdge = if (size <= containerSize && containerSize - pivot < size) containerSize - size else pivot
        return offset - leadingEdge
    }

    private const val PIVOT = 0.3f
}
