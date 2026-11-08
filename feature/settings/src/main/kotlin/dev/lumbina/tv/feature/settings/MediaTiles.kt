package dev.lumbina.tv.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.ui.BANNER_ASPECT
import dev.lumbina.tv.core.ui.MediaThumbnail
import dev.lumbina.tv.core.ui.MutedVideo
import dev.lumbina.tv.core.ui.OVER_PICTURE
import dev.lumbina.tv.core.ui.TICK_BADGE
import dev.lumbina.tv.core.ui.TickBadge
import dev.lumbina.tv.core.ui.focusGlow
import dev.lumbina.tv.core.ui.highlightBorder
import kotlinx.coroutines.delay

/**
 * How the grid's tiles take the focus: [first] goes to the first tile; a video plays in its tile
 * only while [previews], not under a menu or a file looked at.
 */
internal class TileFocus(val first: FocusRequester, val previews: Boolean)

/** The library to look through: the files for the [filter], then the banners; OK on one opens its menu. */
internal fun LazyGridScope.browseTiles(
    state: MediaLibraryState,
    filter: MediaFilter,
    focus: TileFocus,
    onMenu: (MediaEntry) -> Unit,
) {
    val first = focus.first
    val previews = focus.previews
    val files = when (filter) {
        MediaFilter.All -> state.files
        MediaFilter.Wallpaper -> state.files.filter { it.onWallpaper }
        MediaFilter.Screensaver -> state.files.filter { it.onScreensaver }
        MediaFilter.Banners -> emptyList()
        MediaFilter.Nowhere -> state.files.filter { it.nowhere }
    }
    val banners = when (filter) {
        MediaFilter.All, MediaFilter.Banners -> state.banners
        MediaFilter.Nowhere -> state.banners.filter { it.unusedBanner }
        else -> emptyList()
    }
    if (files.isEmpty() && banners.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { Empty(filter) }
        return
    }
    if (files.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { GroupTitle(stringResource(R.string.media_group_files), null) }
        items(files, key = { it.file.id }) { entry ->
            MediaTile(entry, onClick = { onMenu(entry) }, focus = first.takeIf { entry == files.first() }, previews) {
                Labels(marks(entry))
            }
        }
    }
    if (banners.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GroupTitle(stringResource(R.string.media_group_banners), stringResource(R.string.media_group_banners_hint))
        }
        items(banners, key = { it.file.id }) { entry ->
            val takes = first.takeIf { files.isEmpty() && entry == banners.first() }
            MediaTile(entry, onClick = { onMenu(entry) }, focus = takes, previews) {
                val tiles = entry.tileNames().ifEmpty { stringResource(R.string.media_banner_unused) }
                Labels(listOf(tiles to false))
            }
        }
    }
}

/** Every file with a tick for [pick]: OK sets or clears it, holding OK opens the file's menu. */
internal fun LazyGridScope.pickTiles(
    files: List<MediaEntry>,
    pick: MediaMark,
    focus: TileFocus,
    commands: MediaCommands,
    onMenu: (MediaEntry) -> Unit,
) {
    if (files.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { Empty(MediaFilter.All) }
        return
    }
    items(files, key = { it.file.id }) { entry ->
        val on = entry.has(pick)
        MediaTile(
            entry,
            onClick = { commands.mark(entry, pick, !on) },
            onLongClick = { onMenu(entry) },
            focus = focus.first.takeIf { entry == files.first() },
            previews = focus.previews,
        ) {
            TickBadge(on, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
    }
}

/**
 * A file's 16:9 tile: its picture (a video's frame), what kind it is, and [over] laid on it. A
 * video plays in it, silent, once the focus has stayed on it a moment.
 */
@Composable
private fun MediaTile(
    entry: MediaEntry,
    onClick: () -> Unit,
    focus: FocusRequester?,
    previews: Boolean,
    onLongClick: (() -> Unit)? = null,
    over: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        border = highlightBorder(),
        glow = focusGlow(),
        modifier = Modifier
            .aspectRatio(BANNER_ASPECT)
            .onFocusChanged { focused = it.hasFocus }
            .then(focus?.let { Modifier.focusRequester(it) } ?: Modifier),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = picture(entry),
                contentDescription = entry.file.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (entry.unplayable) DIM_ALPHA else 1f),
            )
            if (previews && focused && entry.playsInTile) Preview(entry.file.uri)
            // Clear of the pick mode's tick at the other corner.
            val corner = Modifier.align(Alignment.TopEnd).padding(start = TICK_BADGE)
            when {
                entry.unplayable -> Badge(stringResource(R.string.media_unplayable), Color(UNPLAYABLE), corner)
                entry.file.kind == MediaKind.Video -> Badge(stringResource(R.string.media_video), OVER_PICTURE, corner)
            }
            over()
        }
    }
}

/**
 * The video playing over its frame: only after a moment, so the focus passing through a row
 * starts no players, and seen once it has a picture, so the tile never goes black.
 */
@Composable
private fun Preview(uri: String) {
    var started by remember(uri) { mutableStateOf(false) }
    var shown by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        delay(PREVIEW_DELAY_MILLIS)
        started = true
    }
    if (!started) return
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(PREVIEW_FADE_MILLIS), label = "preview")
    MutedVideo(uri, Modifier.graphicsLayer { this.alpha = alpha }, onFirstFrame = { shown = true })
}

/**
 * What the tile shows: a small copy of the photo, or of a video's frame a second in (many start
 * from black), made once and kept ([MediaThumbnail]).
 */
internal fun picture(entry: MediaEntry): MediaThumbnail =
    MediaThumbnail(entry.file.uri, VIDEO_FRAME_MILLIS.takeIf { entry.file.kind == MediaKind.Video })

/** A video that may play in its tile: not one the device failed to play. */
private val MediaEntry.playsInTile: Boolean get() = file.kind == MediaKind.Video && !unplayable

/** The marks a file has, or that it has none. */
@Composable
private fun marks(entry: MediaEntry): List<Pair<String, Boolean>> = buildList {
    if (entry.onWallpaper) add(stringResource(R.string.media_mark_wallpaper) to true)
    if (entry.onScreensaver) add(stringResource(R.string.media_mark_screensaver) to true)
    if (isEmpty()) add(stringResource(R.string.media_mark_nowhere) to false)
}

/** Small labels at the tile's bottom: light for a mark it has, dark and outlined for "none". */
@Composable
private fun BoxScope.Labels(labels: List<Pair<String, Boolean>>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
    ) {
        labels.forEach { (text, light) ->
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = if (light) Color(LABEL_DARK) else Color.White.copy(alpha = TEXT_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(if (light) Color.White else OVER_PICTURE, LABEL_SHAPE)
                    .then(if (light) Modifier else Modifier.border(1.dp, RING, LABEL_SHAPE))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(6.dp).background(color, LABEL_SHAPE).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun GroupTitle(title: String, hint: String?) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        hint?.let {
            Spacer(Modifier.size(12.dp))
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Empty(filter: MediaFilter) {
    Text(
        stringResource(if (filter == MediaFilter.All) R.string.media_empty else R.string.media_empty_filter),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
    )
}

private const val DIM_ALPHA = 0.45f
private const val VIDEO_FRAME_MILLIS = 1_000L
private const val PREVIEW_DELAY_MILLIS = 600L
private const val PREVIEW_FADE_MILLIS = 300
private const val TEXT_ALPHA = 0.8f
private const val UNPLAYABLE = 0xFFB3261E
private const val LABEL_DARK = 0xFF15181F
private val RING = Color.White.copy(alpha = 0.45f)
private val LABEL_SHAPE = RoundedCornerShape(5.dp)
