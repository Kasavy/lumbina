package dev.lumbina.tv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.TileNames

/**
 * A program an app published (a recommendation or something to continue watching): its artwork
 * at [height] in the program's own shape, a progress bar when it has one, and its title as [name]
 * says: under the card, or on it over the progress. When the app gave no shape, the loaded
 * picture's proportions are used (16:9 until then). Without artwork the title fills the card.
 */
@Composable
fun ProgramTile(
    card: ProgramCard,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    name: TileName = TileName(TileNames.All, NamesPlace.Under),
) {
    var ratio by remember(card.id, card.shape) { mutableFloatStateOf(card.shape?.widthToHeight ?: BANNER_ASPECT) }
    var focused by remember { mutableStateOf(false) }
    val width = height * ratio
    val title = line(card.title, card.subtitle)
    val named = name.shown(focused)
    Column(verticalArrangement = Arrangement.spacedBy(captionGap(height)), modifier = modifier.width(width)) {
        TileCard(
            onClick = onClick,
            modifier = Modifier.height(height).fillMaxWidth().onFocusChanged { focused = it.isFocused },
        ) {
            if (card.artUri != null) {
                AsyncImage(
                    model = card.artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { loaded ->
                        val size = loaded.painter.intrinsicSize
                        if (card.shape == null && size.width > 0f && size.height > 0f) {
                            ratio = (size.width / size.height).coerceIn(MIN_RATIO, MAX_RATIO)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall.onTile(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
            }
            // On the card the title stands over the progress, which keeps the very bottom.
            if (named && !name.under && card.artUri != null) NameOnTile(title)
            card.progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomStart)) }
        }
        if (name.under) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp).drawWithContent { if (named) drawContent() },
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = TRACK_ALPHA))) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private const val TRACK_ALPHA = 0.5f

// Keeps odd pictures (a thin strip, a tall screenshot) from making absurd tiles.
private const val MIN_RATIO = 0.5f
private const val MAX_RATIO = 2.4f
private const val FOCUS_GROWTH = 0.1f

/** A focused card grows by about a tenth; the gap keeps its caption from being covered. */
private fun captionGap(height: Dp): Dp = 6.dp + height * FOCUS_GROWTH / 2
