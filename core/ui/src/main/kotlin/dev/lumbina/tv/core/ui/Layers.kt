package dev.lumbina.tv.core.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Whether a Compose layer (what scaling, fading or clipping a piece of the screen takes) is the
 * system's own render node. From Android 10 it is. On Android 9, as seen on a Mi Box 4, each
 * layer is a View: slow to make, and a fading one is drawn off screen on every frame. Older
 * versions were not looked at and are treated as Android 9.
 */
val LAYERS_ARE_CHEAP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/**
 * What is drawn here cut to [shape]: by a layer where layers are cheap ([LAYERS_ARE_CHEAP]),
 * elsewhere as it draws. Only the drawing is cut, as a layer's clip does too.
 */
fun Modifier.clipShape(shape: Shape): Modifier = if (LAYERS_ARE_CHEAP) {
    clip(shape)
} else {
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
    }
}

/**
 * A [bitmap] as foundation's `Image` shows it, cut to its bounds as it draws. `Image` cuts it by
 * a layer, which on Android 9 is one more View for every icon of a row.
 */
@Composable
fun Picture(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    val described = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    val clipped = Modifier.drawWithContent { clipRect { this@drawWithContent.drawContent() } }
    Layout(modifier.then(described).then(clipped).paint(painter, contentScale = contentScale)) { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {}
    }
}
