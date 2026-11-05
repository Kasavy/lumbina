package dev.lumbina.tv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The tick of every place where tiles are picked (apps for a row, pictures for the wallpaper):
 * the accent with ✓ in a ring of the panel's color when [checked], a white ring on a dark circle
 * when not, in either theme, so it shows on a photo as on the panel. The accent is laid inside the
 * ring, so none of it shows round the ring's edge.
 */
@Composable
fun TickBadge(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TICK_BADGE)
            .then(
                if (checked) {
                    Modifier
                        .background(colors.surface, CircleShape)
                        .padding(RING)
                        .background(colors.primary, CircleShape)
                } else {
                    Modifier.background(OVER_PICTURE, CircleShape).border(RING, Color.White, CircleShape)
                },
            ),
    ) {
        if (checked) Text(TICK, style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
    }
}

/** A tick's size. */
val TICK_BADGE = 24.dp
private val RING = 2.dp

/** A dark ground for a mark or a label laid over a picture, readable whatever is under it. */
val OVER_PICTURE = Color.Black.copy(alpha = 0.6f)
