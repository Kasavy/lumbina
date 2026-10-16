package dev.lumbina.tv.core.ui

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.contentColorFor

/**
 * The card of a tile in [containerColor] ([focusedContainerColor] while focused): OK runs
 * [onClick], holding it [onLongClick]. Focused, it grows by a tenth, glows ([focusGlow]) and is
 * framed in the accent, as [highlighted] frames it always ([highlightBorder]); while OK is down it
 * is back to its own size, without the glow.
 *
 * Where layers are cheap ([LAYERS_ARE_CHEAP]) it is tv-material's `Card`. That card's scale, clip
 * and fades are three layers, and on Android 9 each is a View made when the tile is first placed,
 * which a step to a row not yet shown waited on. There the card is drawn instead, scaled, clipped,
 * framed and glowing as it draws, with no layer of its own.
 */
@Composable
fun TileCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor: Color = containerColor,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LAYERS_ARE_CHEAP) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            colors = CardDefaults.colors(
                containerColor = containerColor,
                focusedContainerColor = focusedContainerColor,
            ),
            border = highlightBorder(highlighted),
            glow = focusGlow(),
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    } else {
        val look = CardLook(
            container = containerColor,
            focusedContainer = focusedContainerColor,
            accent = MaterialTheme.colorScheme.primary,
            glow = MaterialTheme.colorScheme.primary.copy(alpha = GLOW_ALPHA),
            highlighted = highlighted,
        )
        DrawnCard(onClick, onLongClick, look, modifier = modifier, content = content)
    }
}

/** The accent marks the focused tile (and one being moved), so the chosen accent color shows where it matters. */
@Composable
fun highlightBorder(highlighted: Boolean = false): CardBorder {
    val accent = Border(BorderStroke(FRAME, MaterialTheme.colorScheme.primary))
    return if (highlighted) {
        CardDefaults.border(border = accent, focusedBorder = accent)
    } else {
        CardDefaults.border(focusedBorder = accent)
    }
}

/** A soft accent glow under the focused tile. */
@Composable
fun focusGlow(): CardGlow =
    CardDefaults.glow(focusedGlow = Glow(MaterialTheme.colorScheme.primary.copy(alpha = GLOW_ALPHA), GLOW))

/**
 * Type for text on a tile, which grows and shrinks with it: laid out for motion, so its letters
 * keep their places as the tile scales instead of jumping from pixel to pixel.
 */
fun TextStyle.onTile(): TextStyle = copy(textMotion = TextMotion.Animated)

@Composable
private fun DrawnCard(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    look: CardLook,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused = interactions.collectIsFocusedAsState()
    val ok = remember { OkPress() }
    val grow = focused.value && !ok.pressed
    val grown = animateFloatAsState(
        targetValue = if (grow) FOCUSED_SCALE else 1f,
        animationSpec = tween(
            durationMillis = when {
                ok.pressed -> PRESS_MILLIS
                grow -> GROW_MILLIS
                else -> SHRINK_MILLIS
            },
            easing = SCALE_EASING,
        ),
        label = "tile scale",
    )
    val glowing = remember { GlowShown(focused, ok) }
    val drawn = remember(look) { Modifier.drawnCard(look, focused, glowing, grown) }
    CompositionLocalProvider(LocalContentColor provides contentColorFor(look.container)) {
        Box(
            propagateMinConstraints = true,
            modifier = modifier
                .okPress(ok, onClick, onLongClick)
                // Focusable by itself; there for a pointer and for accessibility too. OK is taken above.
                .combinedClickable(
                    interactionSource = interactions,
                    indication = null,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .then(drawn),
        ) {
            // The content's own box, as in tv-material's card: what the content sizes or aligns by
            // itself (a progress bar at the bottom) is not stretched to the whole card.
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

private data class CardLook(
    val container: Color,
    val focusedContainer: Color,
    val accent: Color,
    val glow: Color,
    val highlighted: Boolean,
)

/** The glow is on while the card is focused and OK is not down on it. */
private class GlowShown(private val focused: State<Boolean>, private val ok: OkPress) : State<Boolean> {
    override val value: Boolean get() = focused.value && !ok.pressed
}

/** The card's look drawn around its content, the states read as it draws. */
private fun Modifier.drawnCard(
    look: CardLook,
    focused: State<Boolean>,
    glowing: State<Boolean>,
    grown: State<Float>,
): Modifier = drawWithCache {
    val path = Path().apply { addOutline(SHAPE.createOutline(size, layoutDirection, this@drawWithCache)) }
    // As tv-material's border: over the card's edge, half of it on the card and half outside.
    val frame = Stroke(width = FRAME.toPx(), cap = StrokeCap.Round)
    val corner = SHAPE_CORNER.toPx()
    // As tv-material's glow: the paint itself is clear, only its shadow shows.
    val glow = android.graphics.Paint().apply {
        isAntiAlias = true
        color = look.glow.copy(alpha = 0f).toArgb()
        setShadowLayer(GLOW.toPx(), 0f, 0f, look.glow.toArgb())
    }
    onDrawWithContent {
        scale(grown.value) {
            // The GPU draws shadows from Android 9; this card is drawn below 10, so on 9 alone.
            if (glowing.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner, corner, glow) }
            }
            clipPath(path) {
                drawRect(if (focused.value) look.focusedContainer else look.container)
                this@onDrawWithContent.drawContent()
            }
            if (focused.value || look.highlighted) drawPath(path, look.accent, style = frame)
        }
    }
}

// As tv-material's card: its shape, and its scale with the timing and easing of it.
private val SHAPE_CORNER: Dp = 8.dp
private val SHAPE = RoundedCornerShape(SHAPE_CORNER)
private const val FOCUSED_SCALE = 1.1f
private const val GROW_MILLIS = 300
private const val SHRINK_MILLIS = 500
private const val PRESS_MILLIS = 120
private val SCALE_EASING = CubicBezierEasing(0f, 0f, 0.2f, 1f)

private val FRAME: Dp = 3.dp
private val GLOW: Dp = 16.dp
private const val GLOW_ALPHA = 0.45f
