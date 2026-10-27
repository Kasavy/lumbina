package dev.lumbina.tv.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.BANNER_ASPECT
import dev.lumbina.tv.core.ui.NameOnTile
import dev.lumbina.tv.core.ui.TileCard
import dev.lumbina.tv.core.ui.TileName
import dev.lumbina.tv.core.ui.onTile

/**
 * The tile that opens the "All apps" screen: in a row, where the user moves it or takes it out
 * ([onLongClick]), and at the end of "Apps" ([label] "See all"). As [IconTile].
 */
@Composable
internal fun AllAppsTile(
    onClick: () -> Unit,
    label: String,
    name: TileName,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
) = IconTile(R.drawable.ic_apps, onClick, label, name, modifier, onLongClick, highlighted)

/** The tile that switches to a video input, under its [label] ("HDMI 1" or the user's name). As [IconTile]. */
@Composable
internal fun InputTile(
    onClick: () -> Unit,
    label: String,
    name: TileName,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
) = IconTile(R.drawable.ic_input_hdmi, onClick, label, name, modifier, onLongClick, highlighted)

/**
 * A tile of Lumbina's own, not an app's: its [icon] with its [label] on it, under the icon; where
 * the row's names are shown ([name]), the label stands where every tile's name does, in the same
 * type: under the tile, or at its bottom. [highlighted] while it is moved or its menu is open.
 */
@Composable
private fun IconTile(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    label: String,
    name: TileName,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val onTile = !name.under && name.shown(focused)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        TileCard(
            onClick = onClick,
            onLongClick = onLongClick,
            highlighted = highlighted,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TILE_ALPHA),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BANNER_ASPECT)
                .onFocusChanged { focused = it.isFocused },
        ) {
            if (onTile) {
                // As any tile's name: at its bottom, the icon alone above it.
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-12).dp).size(36.dp),
                )
                NameOnTile(label)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(painterResource(icon), contentDescription = null, Modifier.size(36.dp))
                    if (!name.under) FittedLabel(label)
                }
            }
        }
        if (name.under) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp).drawWithContent {
                    if (name.shown(focused)) drawContent()
                },
            )
        }
    }
}

/**
 * One line that shrinks to the tile: "Все приложения" is wider than it, more so with large text.
 * Cut with "…" only if even the smallest size does not fit.
 */
@Composable
private fun FittedLabel(text: String) {
    val type = MaterialTheme.typography
    BasicText(
        text = text,
        style = type.titleMedium.copy(color = LocalContentColor.current, textAlign = TextAlign.Center).onTile(),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = type.labelSmall.fontSize,
            maxFontSize = type.titleMedium.fontSize,
        ),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** How much of the backdrop Lumbina's own tiles let through: none quite, a little of a photo. */
private const val TILE_ALPHA = 0.85f
