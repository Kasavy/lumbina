package dev.lumbina.tv.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Opens a section or a tab once the focus rests on it: passing over it on the way to another
 * doesn't build what it shows. [pending] is the one focused but not opened yet.
 */
class OpenOnRest<T> internal constructor(private val open: State<(T) -> Unit>) {
    var pending by mutableStateOf<T?>(null)
        internal set

    fun now(entry: T) {
        pending = null
        open.value(entry)
    }
}

@Composable
fun <T> rememberOpenOnRest(open: (T) -> Unit): OpenOnRest<T> {
    val latest = rememberUpdatedState(open)
    val resting = remember { OpenOnRest(latest) }
    LaunchedEffect(resting) {
        // Watched here, not read in composition: the focus moving on recomposes nothing by itself.
        snapshotFlow { resting.pending }.collectLatest { entry ->
            if (entry == null) return@collectLatest
            delay(OPEN_ON_REST_MILLIS)
            // The focus may have just moved on, before this wait was cancelled: then it is the
            // next one's turn, and opening this one would also forget that one.
            if (resting.pending == entry) resting.now(entry)
        }
    }
    return resting
}

/**
 * [entry] opens when the focus rests on it. [enabled] is asked each time the focus comes, so it
 * may change without the row being composed again. [leave] is the key that goes into what the
 * entry shows: pressed before the entry has opened, it opens it at once, and the next press goes in.
 */
fun <T> Modifier.opensOnRest(
    resting: OpenOnRest<T>,
    entry: T,
    isOpen: Boolean,
    leave: Key,
    enabled: () -> Boolean = { true },
): Modifier = this
    .onFocusChanged { if (enabled() && it.isFocused) resting.pending = entry.takeIf { !isOpen } }
    .onPreviewKeyEvent { event ->
        val leaving = event.type == KeyEventType.KeyDown && event.key == leave
        if (leaving && resting.pending == entry) resting.now(entry)
        leaving && !isOpen
    }

/** Shorter than a deliberate press, longer than a held key's repeat. */
private const val OPEN_ON_REST_MILLIS = 100L
