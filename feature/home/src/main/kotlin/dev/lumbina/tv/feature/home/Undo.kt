package dev.lumbina.tv.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeRow

/** What kind of thing was taken off the screen. */
enum class UndoKind { Hidden, OutOfFavorites, OutOfCategory, FolderDissolved, RowHidden, ShortcutRemoved, NoticesMuted }

/**
 * Something just taken off the screen that can be put back for a few seconds: the [name] of the
 * app, folder or row, the [place] it left where that matters, and what brings it back: the layout
 * as it was [before], the [row] to switch on again, or the app whose notifications were [muted].
 */
data class Undo(
    val kind: UndoKind,
    val name: String,
    val place: String = "",
    internal val before: HomeLayout? = null,
    internal val row: HomeRow? = null,
    /** The package whose notifications were muted. */
    internal val muted: String? = null,
    /** The app taken out of a section, by the section's id: ticking it again in that row undoes this. */
    internal val takenOut: Pair<String, AppId>? = null,
)

/** Where the "Undo" button is, and whether the focus is on it. */
internal class UndoFocus {
    val button = FocusRequester()
    var focused by mutableStateOf(false)
}

/**
 * While something can be undone, the first ▼ from anywhere goes to the "Undo" button: the bar is
 * at the bottom of the screen and the rows between would be in the way. From the button, ▲ goes
 * back up as usual.
 */
private fun Modifier.undoKeys(undo: Undo?, focus: UndoFocus, busy: Boolean): Modifier = onPreviewKeyEvent { event ->
    val down = event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
    val offered = undo != null && !busy && !focus.focused
    if (!offered || !down) return@onPreviewKeyEvent false
    runCatching { focus.button.requestFocus() }.getOrDefault(false)
}

/**
 * [content] with the undo bar over its bottom edge and the ▼ that leads to it. While [busy] (a
 * tile is being moved) the keys are the move's: ▼ would take the focus off the tile for good.
 */
@Composable
internal fun UndoHost(
    undo: Undo?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val focus = remember { UndoFocus() }
    Box(modifier.undoKeys(undo, focus, busy)) {
        content()
        UndoBar(undo, focus, onUndo, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * "The app “YouTube” is hidden · Undo": at the bottom of the screen, never taking the focus by itself;
 * gone after a few seconds or once anything else is changed.
 */
@Composable
private fun UndoBar(undo: Undo?, focus: UndoFocus, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    // The last one stays on screen while the bar fades out.
    var shown by remember { mutableStateOf(undo) }
    if (undo != null) shown = undo
    // Time is up with the focus on the button: back up to the rows before the button goes.
    LaunchedEffect(undo == null) { if (undo == null && focus.focused) focusManager.moveFocus(FocusDirection.Up) }
    AnimatedVisibility(
        visible = undo != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(bottom = BOTTOM_MESSAGE_GAP),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .bottomMessage()
                .padding(start = 26.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = shown?.message().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = {
                    // The button is about to go: the focus returns to the rows first.
                    focusManager.moveFocus(FocusDirection.Up)
                    onUndo()
                },
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.focusRequester(focus.button).onFocusChanged { focus.focused = it.isFocused },
            ) { Text(stringResource(R.string.undo)) }
            Text(
                text = stringResource(R.string.undo_keys),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
}

/**
 * After "Undo" the focus goes to what came back: the tile whose menu took it off the screen. That
 * is where the focus was when the menu opened ([menuOpen]), kept from the moment the change was
 * [offered] back; [undone] counts the offers taken.
 */
@Composable
internal fun FocusOnUndone(focus: HomeFocus, menuOpen: Boolean, offered: Undo?, undone: Int) {
    var menuAt by remember { mutableStateOf<FocusKey?>(null) }
    var gone by remember { mutableStateOf<FocusKey?>(null) }
    LaunchedEffect(menuOpen) { if (menuOpen) menuAt = focus.last }
    // Each offer its own place: one that took nothing off the screen (a row hidden) has none.
    LaunchedEffect(offered) { if (offered != null) gone = menuAt.takeIf { offered.before != null } }
    LaunchedEffect(undone) {
        if (undone == 0) return@LaunchedEffect
        gone?.let { focus.focusSoon(it) }
        gone = null
    }
}

@Composable
private fun Undo.message(): String = when (kind) {
    UndoKind.Hidden -> stringResource(R.string.undo_hidden, name)
    UndoKind.OutOfFavorites -> stringResource(R.string.undo_out_of_favorites, name)
    UndoKind.OutOfCategory -> stringResource(R.string.undo_out_of_category, name, place)
    UndoKind.FolderDissolved -> stringResource(R.string.undo_folder_dissolved, name)
    UndoKind.RowHidden -> stringResource(R.string.undo_row_hidden, name)
    UndoKind.ShortcutRemoved -> stringResource(R.string.undo_shortcut_removed)
    UndoKind.NoticesMuted -> stringResource(R.string.undo_notices_muted, name)
}
