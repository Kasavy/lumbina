package dev.lumbina.tv.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/** Takes the focus once when [wanted]: the row whose page was just left. */
@Composable
internal fun Modifier.focusedOnReturn(wanted: Boolean, onFocus: () -> Unit): Modifier {
    if (!wanted) return this
    val focus = remember { FocusRequester() }
    val done by rememberUpdatedState(onFocus)
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        done()
    }
    return focusRequester(focus)
}
