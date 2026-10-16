package dev.lumbina.tv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.TileNames

/**
 * Which tiles of a row show their name ([names]) and where ([place]): on the tile over a shade,
 * taking no room, or under it, in a line every tile of the row keeps so the row does not jump as
 * the focus moves along it.
 */
@Immutable
data class TileName(val names: TileNames, val place: NamesPlace) {
    /** Whether a tile reserves the line under it. */
    val under: Boolean get() = names != TileNames.None && place == NamesPlace.Under

    /** Whether a tile shows its name now. */
    fun shown(focused: Boolean): Boolean = names == TileNames.All || (names == TileNames.Focused && focused)

    companion object {
        val None = TileName(TileNames.None, NamesPlace.OnTile)
    }
}

/**
 * A 16:9 app tile, its name shown as [name] says. [highlighted] marks it with an accent border,
 * e.g. while it is being moved. [banner]: the user's own picture instead of the app's banner.
 * [caption], a word on where the app is, stands dimmer in the line under the tile while the name
 * is not shown there.
 */
@Composable
fun AppCard(
    app: App,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    name: TileName = TileName.None,
    banner: String? = null,
    caption: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val named = name.shown(focused)
    val under = name.under || caption != null
    val label = if (named || caption == null) app.label else caption
    Tile(label = label.takeIf { under }, modifier = modifier, shown = named || caption != null, dim = !named) {
        TileCard(
            onClick = onClick,
            onLongClick = onLongClick,
            highlighted = highlighted,
            modifier = Modifier
                .aspectRatio(BANNER_ASPECT)
                .onFocusChanged { focused = it.isFocused },
        ) {
            if (banner != null) {
                AsyncImage(
                    model = banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (named && !under) NameOnTile(app.label)
            } else {
                AppArtwork(app, named = named && !under)
            }
        }
    }
}

/**
 * A folder tile: up to four of its apps in a 2×2 grid; with more, the fourth place says how many
 * more there are ("+5"). Its name and count stand under it only where the row keeps a line for
 * names ([name]); elsewhere the folder is named when opened.
 */
@Composable
fun FolderCard(
    name: String,
    apps: List<App>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    names: TileName = TileName.None,
) {
    var focused by remember { mutableStateOf(false) }
    Tile(
        label = line(name, apps.size.toString()).takeIf {
            names.under
        },
        modifier = modifier,
        shown = names.shown(focused),
    ) {
        TileCard(
            onClick = onClick,
            onLongClick = onLongClick,
            highlighted = highlighted,
            modifier = Modifier.aspectRatio(BANNER_ASPECT).onFocusChanged { focused = it.isFocused },
        ) {
            FolderPreview(apps, Modifier.fillMaxSize().padding(8.dp))
        }
    }
}

/**
 * A folder's apps laid out by how many there are, so none leaves an empty half: one fills the
 * tile, two sit side by side, three and four share a 2×2 grid (the third alone, centered); five
 * and more show three, and how many more in the fourth place.
 */
@Composable
private fun FolderPreview(apps: List<App>, modifier: Modifier = Modifier) {
    val gap = 6.dp
    val more = (apps.size - (PREVIEW_APPS - 1)).takeIf { apps.size > PREVIEW_APPS }
    val shown = if (more != null) apps.take(PREVIEW_APPS - 1) else apps
    Column(verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically), modifier = modifier) {
        when (shown.size) {
            0 -> Unit

            1 -> Mini(shown[0], Modifier.fillMaxSize())

            2 -> Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                shown.forEach { Mini(it, Modifier.weight(1f).aspectRatio(BANNER_ASPECT)) }
            }

            else -> shown.chunked(2).forEachIndexed { line, pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    pair.forEach { Mini(it, Modifier.fillMaxHeight().aspectRatio(BANNER_ASPECT)) }
                    if (more != null && line == 1) More(more, Modifier.fillMaxHeight().aspectRatio(BANNER_ASPECT))
                }
            }
        }
    }
}

@Composable
private fun Mini(app: App, modifier: Modifier = Modifier) {
    Box(modifier.clipShape(RoundedCornerShape(6.dp))) { AppArtwork(app, compact = true) }
}

/** The fourth place of a folder of more apps than it shows: "+N", the ones not shown. */
@Composable
private fun More(count: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(Color.Black.copy(alpha = MORE_ALPHA), RoundedCornerShape(6.dp)),
    ) {
        Text("+$count", style = MaterialTheme.typography.titleSmall.onTile(), fontWeight = FontWeight.Bold)
    }
}

/** A tile's name on the tile itself: at its bottom, over a shade that keeps it legible on any picture. */
@Composable
fun BoxScope.NameOnTile(name: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .fillMaxHeight(SHADE_HEIGHT)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SHADE_ALPHA)))),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.onTile(),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** A tile with a caption under it ([label], none without), drawn only while [shown], quieter when [dim]. */
@Composable
private fun Tile(
    label: String?,
    modifier: Modifier = Modifier,
    shown: Boolean = true,
    dim: Boolean = false,
    card: @Composable () -> Unit,
) {
    // The focused tile grows; the gap keeps its caption from being covered.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        card()
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .align(Alignment.Start)
                    .drawWithContent { if (shown) drawContent() },
            )
        }
    }
}

private const val PREVIEW_APPS = 4
private const val MORE_ALPHA = 0.3f

/** The shade under a name on a tile: how much of the tile's height, and how dark at its bottom. */
private const val SHADE_HEIGHT = 0.5f
private const val SHADE_ALPHA = 0.75f
