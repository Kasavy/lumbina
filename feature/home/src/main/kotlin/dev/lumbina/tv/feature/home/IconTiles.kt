package dev.lumbina.tv.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.device.InputPresence
import dev.lumbina.tv.core.ui.BANNER_ASPECT
import dev.lumbina.tv.core.ui.DIMMED_ALPHA
import dev.lumbina.tv.core.ui.NameOnTile
import dev.lumbina.tv.core.ui.OVER_PICTURE
import dev.lumbina.tv.core.ui.TileCard
import dev.lumbina.tv.core.ui.TileName
import dev.lumbina.tv.core.ui.UserBanner
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
    banner: String? = null,
) = IconTile(R.drawable.ic_apps, onClick, label, name, modifier, onLongClick, highlighted, banner = banner)

/**
 * The tile that switches to a video input, under its [label] ("HDMI 1 · MIBOX4", or the user's
 * name). As [IconTile]; where the device tells what is on the input ([presence]), a mark in its
 * corner: a dot while a device works on it, a ring while a cable is in and the device sleeps; paler
 * while nothing is plugged in. The mark stands at the top right, or the top left where the right
 * is taken ([markAtStart]).
 */
@Composable
internal fun InputTile(
    onClick: () -> Unit,
    label: String,
    name: TileName,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    presence: InputPresence? = null,
    markAtStart: Boolean = false,
    banner: String? = null,
) = IconTile(
    R.drawable.ic_input_hdmi,
    onClick,
    label,
    name,
    modifier,
    onLongClick,
    highlighted,
    faded = presence == InputPresence.Empty,
    mark = when (presence) {
        is InputPresence.Working -> TileMark(filled = true, atStart = markAtStart)
        InputPresence.Connected -> TileMark(filled = false, atStart = markAtStart)
        else -> null
    },
    banner = banner,
)

/** A mark in a tile's corner: a dot ([filled]) or a ring, at the top left ([atStart]) or right. */
internal class TileMark(val filled: Boolean, val atStart: Boolean)

/**
 * A tile of Lumbina's own, not an app's: its [icon] with its [label] on it, under the icon; where
 * the row's names are shown ([name]), the label stands where every tile's name does, in the same
 * type: under the tile, or at its bottom. [highlighted] while it is moved or its menu is open;
 * [mark] in the accent colour in its corner. [faded] pales what is on it and under it, not the
 * tile as a whole: a paled whole would cut off the focus frame, which reaches past its edges.
 * With the user's [banner] the picture fills the tile and the label is named as an app's is: on
 * it or under it as the row's names say, or not at all; the mark gets a dark ground to be seen on
 * any picture.
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
    faded: Boolean = false,
    mark: TileMark? = null,
    banner: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val onTile = !name.under && name.shown(focused)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        TileCard(
            onClick = onClick,
            onLongClick = onLongClick,
            highlighted = highlighted,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
                .copy(alpha = if (faded) TILE_ALPHA * DIMMED_ALPHA else TILE_ALPHA),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BANNER_ASPECT)
                .onFocusChanged { focused = it.isFocused },
        ) {
            Box(Modifier.fillMaxSize().dimmedIf(faded)) {
                if (banner != null) {
                    UserBanner(banner, label)
                    if (onTile) NameOnTile(label)
                } else if (onTile) {
                    // As any tile's name: at its bottom, the icon alone above it.
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).offset(y = (-12).dp).size(36.dp),
                    )
                    NameOnTile(label)
                } else {
                    IconOverLabel(icon, label.takeUnless { name.under })
                }
            }
            mark?.let { Mark(it, onPicture = banner != null) }
        }
        if (name.under) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp).dimmedIf(faded).drawWithContent {
                    if (name.shown(focused)) drawContent()
                },
            )
        }
    }
}

@Composable
private fun BoxScope.Mark(mark: TileMark, onPicture: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .align(if (mark.atStart) Alignment.TopStart else Alignment.TopEnd)
            .padding(12.dp)
            // On a picture, a dark ring round it: the accent alone is lost on a light one.
            .then(if (onPicture) Modifier.background(OVER_PICTURE, CircleShape).padding(MARK_GROUND) else Modifier)
            .size(DOT)
            .then(
                if (mark.filled) {
                    Modifier.background(accent, CircleShape)
                } else {
                    Modifier.border(2.dp, accent, CircleShape)
                },
            ),
    )
}

/**
 * A tile's look without a tile to press, as the banner picker shows it: [icon] on the tiles' own
 * ground, unnamed (the chip over it says what it is, and the panel's title whose tile).
 */
@Composable
internal fun IconArt(@DrawableRes icon: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        IconOverLabel(icon, null)
    }
}

/**
 * [icon] with [label] under it, in the middle of the tile. A small tile (where tiles are picked)
 * has no room for the whole icon over the label: the icon gives way, the label keeps its size and
 * its place.
 */
@Composable
private fun IconOverLabel(@DrawableRes icon: Int, label: String?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            Modifier
                .weight(1f, fill = false)
                .sizeIn(maxWidth = ICON, maxHeight = ICON)
                .aspectRatio(1f, matchHeightConstraintsFirst = true),
        )
        if (label != null) FittedLabel(label)
    }
}

/**
 * One line that shrinks to the tile: "Все приложения" is wider than it, more so with large text.
 * Cut with "…" only if even the smallest size does not fit. A shrunk line keeps the full line's
 * height, so it stands where a short one does. The size is found once, in whole sp, and the line
 * then drawn at it as any other: text sized while the tile grows on focus would shake.
 */
@Composable
private fun FittedLabel(text: String) {
    val type = MaterialTheme.typography
    val style = type.titleMedium.copy(color = LocalContentColor.current, textAlign = TextAlign.Center).onTile()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val full = remember(style, measurer) { measurer.measure(text = "Ag", style = style, maxLines = 1).size.height }
    BoxWithConstraints(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { full.toDp() })
            .padding(horizontal = LABEL_PADDING),
    ) {
        val width = constraints.maxWidth
        val size = remember(text, style, width, measurer) {
            val sizes = generateSequence(style.fontSize.value.toInt()) { it - 1 }
                .takeWhile { it >= type.labelSmall.fontSize.value.toInt() }
            sizes.firstOrNull { sp ->
                measurer.measure(text, style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width <= width
            }?.sp ?: type.labelSmall.fontSize
        }
        BasicText(
            text = text,
            style = style.copy(fontSize = size),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            // Drawn once into a layer of its own, which then grows with the tile as a whole:
            // letters drawn anew at each step of the growth shake.
            modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        )
    }
}

/** How much of the backdrop Lumbina's own tiles let through: none quite, a little of a photo. */
private const val TILE_ALPHA = 0.85f
private val DOT = 10.dp
private val MARK_GROUND = 5.dp
private val ICON = 36.dp
private val LABEL_PADDING = 4.dp
