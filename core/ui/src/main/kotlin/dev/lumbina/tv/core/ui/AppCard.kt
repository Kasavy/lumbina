package dev.lumbina.tv.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.domain.app.App

/**
 * A 16:9 app tile, with the app's name under it when [showLabel]. [highlighted] marks it with
 * an accent border, e.g. while it is being moved. [banner]: the user's own picture instead of
 * the app's banner.
 */
@Composable
fun AppCard(
    app: App,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    showLabel: Boolean = false,
    banner: String? = null,
) {
    Tile(label = app.label.takeIf { showLabel }, modifier = modifier) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            border = highlightBorder(highlighted),
            glow = focusGlow(),
            modifier = Modifier.aspectRatio(BANNER_ASPECT),
        ) {
            if (banner != null) {
                AsyncImage(
                    model = banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AppArtwork(app)
            }
        }
    }
}

/** A folder tile: up to four of its apps in a 2×2 grid, its name, and how many apps it holds. */
@Composable
fun FolderCard(
    name: String,
    apps: List<App>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
) {
    Tile(label = "$name · ${apps.size}", modifier = modifier) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            border = highlightBorder(highlighted),
            glow = focusGlow(),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.aspectRatio(BANNER_ASPECT),
        ) {
            FolderPreview(apps.take(PREVIEW_APPS), Modifier.fillMaxSize().padding(8.dp))
        }
    }
}

/**
 * A folder's apps laid out by how many there are, so none leaves an empty half: one fills the
 * tile, two sit side by side, three and four share a 2×2 grid (the third alone, centered).
 */
@Composable
private fun FolderPreview(apps: List<App>, modifier: Modifier = Modifier) {
    val gap = 6.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically), modifier = modifier) {
        when (apps.size) {
            0 -> Unit

            1 -> Mini(apps[0], Modifier.fillMaxSize())

            2 -> Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                apps.forEach { Mini(it, Modifier.weight(1f).aspectRatio(BANNER_ASPECT)) }
            }

            else -> apps.chunked(2).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    pair.forEach { Mini(it, Modifier.fillMaxHeight().aspectRatio(BANNER_ASPECT)) }
                }
            }
        }
    }
}

@Composable
private fun Mini(app: App, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp))) { AppArtwork(app, compact = true) }
}

/** A tile with an optional caption under it. Folders always show their name. */
@Composable
private fun Tile(label: String?, modifier: Modifier = Modifier, card: @Composable () -> Unit) {
    // The focused tile grows; the gap keeps its caption from being covered.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        card()
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).align(Alignment.Start),
            )
        }
    }
}

/** The accent marks the focused tile (and one being moved), so the chosen accent color shows where it matters. */
@Composable
fun highlightBorder(highlighted: Boolean = false): CardBorder {
    val accent = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary))
    return if (highlighted) {
        CardDefaults.border(border = accent, focusedBorder = accent)
    } else {
        CardDefaults.border(focusedBorder = accent)
    }
}

/** A soft accent glow under the focused tile. */
@Composable
fun focusGlow(): CardGlow =
    CardDefaults.glow(focusedGlow = Glow(MaterialTheme.colorScheme.primary.copy(alpha = GLOW_ALPHA), 16.dp))

private const val BANNER_ASPECT = 16f / 9f
private const val PREVIEW_APPS = 4
private const val GLOW_ALPHA = 0.45f
