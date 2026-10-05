package dev.lumbina.tv.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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

private object PivotSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val pivot = containerSize * PIVOT
        // An item that doesn't fit below the pivot is aligned to the far edge instead.
        val leadingEdge = if (size <= containerSize && containerSize - pivot < size) containerSize - size else pivot
        return offset - leadingEdge
    }

    private const val PIVOT = 0.3f
}
