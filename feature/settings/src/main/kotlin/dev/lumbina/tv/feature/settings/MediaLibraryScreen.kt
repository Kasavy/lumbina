package dev.lumbina.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.ui.ConfirmDialog
import dev.lumbina.tv.core.ui.requestFocusSoon
import kotlinx.coroutines.delay

/**
 * How the library opens: [pick] set, as a list to tick every file for that mark (from the
 * wallpaper's or screensaver's "My photos and videos"); otherwise to look through, starting at [filter].
 */
data class MediaView(val pick: MediaMark? = null, val filter: MediaFilter = MediaFilter.All) {
    /** For a navigation route. */
    fun encode(): String = pick?.let { "pick-${it.name}" } ?: "browse-${filter.name}"

    companion object {
        fun decode(text: String?): MediaView {
            val how = text.orEmpty().substringBefore('-')
            val what = text.orEmpty().substringAfter('-', "")
            return if (how == "pick") {
                MediaView(pick = MediaMark.entries.firstOrNull { it.name == what } ?: MediaMark.Screensaver)
            } else {
                MediaView(filter = MediaFilter.entries.firstOrNull { it.name == what } ?: MediaFilter.All)
            }
        }
    }
}

/**
 * The user's photos, videos and banners on a screen of their own, over the dimmed home screen.
 * To look through: the marks on each file, filters, OK opens a file's menu. To pick ([MediaView.pick]):
 * a tick on each file, OK sets or clears it, holding OK opens the menu. Back closes it ([onBack]).
 */
@Suppress("ViewModelForwarding") // the screen takes it only as its commands
@Composable
fun MediaLibraryRoute(
    view: MediaView,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA))) {
        state?.let { MediaLibrary(it, view, viewModel) }
    }
}

@Composable
private fun MediaLibrary(state: MediaLibraryState, view: MediaView, commands: MediaCommands) {
    var filter by rememberSaveable { mutableStateOf(view.filter) }
    var menu by remember { mutableStateOf<MediaEntry?>(null) }
    var cleaning by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val pick = view.pick
    var holdsFocus by remember { mutableStateOf(false) }
    // The tile with the focus gone (deleted, made a banner, out of the filter): the focus would be
    // nowhere and the remote seem dead. A moment first: closing a menu brings it back by itself.
    LaunchedEffect(holdsFocus, menu) {
        if (holdsFocus || menu != null) return@LaunchedEffect
        delay(FOCUS_BACK_MILLIS)
        first.requestFocusSoon()
    }
    Column(
        Modifier
            .fillMaxSize()
            .onFocusChanged { holdsFocus = it.hasFocus }
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        if (pick != null) {
            PickHeading(state, pick, commands)
        } else {
            BrowseHeading(state, filter, onFilter = { filter = it }, onCleanUp = { cleaning = true })
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            // Room for the focused tile to grow.
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (pick != null) {
                pickTiles(state.files, pick, first, commands, onMenu = { menu = it })
            } else {
                browseTiles(state, filter, first, onMenu = { menu = it })
            }
        }
        if (pick != null) {
            Text(
                stringResource(R.string.media_pick_keys),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        LaunchedEffect(Unit) { first.requestFocusSoon() }
        menu?.let { opened ->
            // The entry as it is now: the menu's switches show what was just changed.
            val now = (state.files + state.banners).firstOrNull { it.file.id == opened.file.id }
            if (now == null) menu = null else MediaMenu(now, pick, commands, onDismiss = { menu = null })
        }
        if (cleaning) CleanUpDialog(state.unused, commands::deleteUnused, onDismiss = { cleaning = false })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrowseHeading(
    state: MediaLibraryState,
    filter: MediaFilter,
    onFilter: (MediaFilter) -> Unit,
    onCleanUp: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.media_title), style = MaterialTheme.typography.displaySmall)
                Subtitle(countsLine(state))
            }
            if (state.unused > 0) {
                Button(onClick = onCleanUp) { Text(stringResource(R.string.media_delete_unused, state.unused)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            MediaFilter.entries.forEach { each ->
                FilterChip(selected = each == filter, onClick = { onFilter(each) }) {
                    Text(stringResource(R.string.media_filter, stringResource(each.title), state.count(each)))
                }
            }
        }
    }
}

/** The heading of the library's ticks for this mark. */
private val MediaMark.pickTitle: Int
    get() = when (this) {
        MediaMark.Screensaver -> R.string.media_pick_screensaver
        MediaMark.Wallpaper -> R.string.media_pick_wallpaper
    }

@Composable
private fun PickHeading(state: MediaLibraryState, pick: MediaMark, commands: MediaCommands) {
    val chosen = state.files.count { it.has(pick) }
    val all = chosen == state.files.size
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(pick.pickTitle), style = MaterialTheme.typography.displaySmall)
            Subtitle(stringResource(R.string.media_chosen, chosen, state.files.size))
        }
        if (state.files.isNotEmpty()) {
            Button(onClick = { commands.markAll(pick, on = !all) }) {
                Text(stringResource(if (all) R.string.media_clear_all else R.string.media_choose_all))
            }
        }
    }
}

@Composable
private fun Subtitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Delete unused" asks first, with how many files go: one OK is too easy on a remote. */
@Composable
private fun CleanUpDialog(files: Int, onDelete: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.media_delete_unused_title),
        text = stringResource(
            R.string.media_delete_unused_text,
            pluralStringResource(R.plurals.wallpaper_files, files, files),
        ),
        confirmLabel = stringResource(R.string.wallpaper_delete_media_confirm),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = {
            onDelete()
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun countsLine(state: MediaLibraryState): String {
    val photos = state.files.count { it.file.kind == MediaKind.Photo }
    val videos = state.files.size - photos
    val banners = state.banners.size
    return listOfNotNull(
        pluralStringResource(R.plurals.media_photos, photos, photos),
        pluralStringResource(R.plurals.media_videos, videos, videos).takeIf { videos > 0 },
        pluralStringResource(R.plurals.media_banners, banners, banners).takeIf { banners > 0 },
    ).joinToString()
}

private val MediaFilter.title: Int
    get() = when (this) {
        MediaFilter.All -> R.string.media_filter_all
        MediaFilter.Wallpaper -> R.string.media_mark_wallpaper
        MediaFilter.Screensaver -> R.string.media_mark_screensaver
        MediaFilter.Banners -> R.string.media_filter_banners
        MediaFilter.Nowhere -> R.string.media_mark_nowhere
    }

private const val COLUMNS = 5
private const val SCRIM_ALPHA = 0.97f
private const val FOCUS_BACK_MILLIS = 300L
