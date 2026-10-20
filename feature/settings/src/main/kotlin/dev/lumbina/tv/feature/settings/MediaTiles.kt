package dev.lumbina.tv.feature.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.ui.BANNER_ASPECT
import dev.lumbina.tv.core.ui.focusGlow
import dev.lumbina.tv.core.ui.highlightBorder

/** The library to look through: the files for the [filter], then the banners; OK on one opens its menu. */
internal fun LazyGridScope.browseTiles(
    state: MediaLibraryState,
    filter: MediaFilter,
    first: FocusRequester,
    onMenu: (MediaEntry) -> Unit,
) {
    val files = when (filter) {
        MediaFilter.All -> state.files
        MediaFilter.Wallpaper -> state.files.filter { it.onWallpaper }
        MediaFilter.Screensaver -> state.files.filter { it.onScreensaver }
        MediaFilter.Banners -> emptyList()
        MediaFilter.Nowhere -> state.files.filter { it.nowhere }
    }
    val banners = when (filter) {
        MediaFilter.All, MediaFilter.Banners -> state.banners
        MediaFilter.Nowhere -> state.banners.filter { it.shownOn.isEmpty() }
        else -> emptyList()
    }
    if (files.isEmpty() && banners.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { Empty(filter) }
        return
    }
    if (files.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { GroupTitle(stringResource(R.string.media_group_files), null) }
        items(files, key = { it.file.id }) { entry ->
            MediaTile(entry, onClick = { onMenu(entry) }, focus = first.takeIf { entry == files.first() }) {
                Labels(marks(entry))
            }
        }
    }
    if (banners.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GroupTitle(stringResource(R.string.media_group_banners), stringResource(R.string.media_group_banners_hint))
        }
        items(banners, key = { it.file.id }) { entry ->
            val focus = first.takeIf { files.isEmpty() && entry == banners.first() }
            MediaTile(entry, onClick = { onMenu(entry) }, focus = focus) {
                val tiles = entry.shownOn.joinToString().ifEmpty { stringResource(R.string.media_banner_unused) }
                Labels(listOf(tiles to false))
            }
        }
    }
}

/** Every file with a tick for [pick]: OK sets or clears it, holding OK opens the file's menu. */
internal fun LazyGridScope.pickTiles(
    files: List<MediaEntry>,
    pick: MediaMark,
    first: FocusRequester,
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
            focus = first.takeIf { entry == files.first() },
        ) {
            Tick(on, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
    }
}

/** A file's 16:9 tile: its picture (a video's first frame), what kind it is, and [over] laid on it. */
@Composable
private fun MediaTile(
    entry: MediaEntry,
    onClick: () -> Unit,
    focus: FocusRequester?,
    onLongClick: (() -> Unit)? = null,
    over: @Composable BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        border = highlightBorder(),
        glow = focusGlow(),
        modifier = Modifier.aspectRatio(BANNER_ASPECT).then(focus?.let { Modifier.focusRequester(it) } ?: Modifier),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = picture(entry),
                contentDescription = entry.file.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (entry.unplayable) DIM_ALPHA else 1f),
            )
            // Clear of the pick mode's tick at the other corner.
            val corner = Modifier.align(Alignment.TopEnd).padding(start = TICK_SIZE)
            when {
                entry.unplayable -> Badge(stringResource(R.string.media_unplayable), Color(UNPLAYABLE), corner)
                entry.file.kind == MediaKind.Video -> Badge(stringResource(R.string.media_video), DARK, corner)
            }
            over()
        }
    }
}

/** What the tile shows: the photo, or a frame of the video a second in (many start from black). */
@Composable
private fun picture(entry: MediaEntry): Any {
    if (entry.file.kind != MediaKind.Video) return entry.file.uri
    val context = LocalPlatformContext.current
    return remember(entry.file.uri) {
        ImageRequest.Builder(context).data(entry.file.uri).videoFrameMillis(VIDEO_FRAME_MILLIS).build()
    }
}

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
                    .background(if (light) Color.White else DARK, LABEL_SHAPE)
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

/** The pick mode's tick: filled with the accent and ✓ when set, an empty ring when not. */
@Composable
private fun Tick(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TICK_SIZE)
            .background(if (on) MaterialTheme.colorScheme.primary else DARK, CircleShape)
            .then(if (on) Modifier else Modifier.border(2.dp, Color.White, CircleShape)),
    ) {
        if (on) Text("✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
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
private const val TEXT_ALPHA = 0.8f
private const val UNPLAYABLE = 0xFFB3261E
private const val LABEL_DARK = 0xFF15181F
private val DARK = Color.Black.copy(alpha = 0.6f)
private val RING = Color.White.copy(alpha = 0.45f)
private val LABEL_SHAPE = RoundedCornerShape(5.dp)
private val TICK_SIZE = 24.dp
