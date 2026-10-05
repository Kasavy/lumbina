package dev.lumbina.tv.core.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loaded artwork: a TV banner (16:9) or, for phone apps, the launcher icon and a color taken from it. */
private sealed interface Artwork {
    data class Banner(val image: ImageBitmap) : Artwork
    data class Icon(val image: ImageBitmap, val color: Color) : Artwork
    data object None : Artwork
}

/**
 * Shows an app's TV banner filling the tile. An app without one gets a banner made from its icon:
 * the icon's own color (darkened, so white text stays readable) behind the icon and the name.
 */
@Composable
fun AppArtwork(app: App, modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    // Keyed on the app so a reused slot never shows the previous app's artwork.
    var loaded by remember(app.id, app.updatedAt) { mutableStateOf(ArtworkCache[app]) }
    LaunchedEffect(app.id, app.updatedAt) {
        if (loaded == null) {
            loaded =
                withContext(Dispatchers.IO) { context.packageManager.loadArtwork(app) }.also { ArtworkCache[app] = it }
        }
    }
    val artwork = loaded ?: Artwork.None

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (artwork) {
            is Artwork.Banner -> Image(
                bitmap = artwork.image,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            is Artwork.Icon -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().background(artwork.color),
            ) {
                if (compact) {
                    Image(
                        bitmap = artwork.image,
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxSize(COMPACT_ICON),
                    )
                } else {
                    IconWithLabel(artwork.image, app.label)
                }
            }

            Artwork.None -> if (!compact) Label(app.label)
        }
    }
}

@Composable
private fun IconWithLabel(icon: ImageBitmap, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

private fun PackageManager.loadArtwork(app: App): Artwork {
    val component = ComponentName(app.id.packageName, app.id.activityName)
    val banner = runCatching { getActivityBanner(component) ?: getApplicationBanner(app.id.packageName) }.getOrNull()
    if (banner != null) return Artwork.Banner(banner.toImage(BANNER_WIDTH, BANNER_HEIGHT))
    val icon = runCatching { getActivityIcon(component) }.getOrNull() ?: return Artwork.None
    val bitmap = icon.toBitmap(ICON_SIZE, ICON_SIZE)
    return Artwork.Icon(bitmap.asImageBitmap(), bannerColorOf(bitmap))
}

/**
 * The icon's dominant color for a banner behind it: the average of its opaque, colorful pixels
 * (grey edges and white backgrounds say little), darkened.
 */
private fun bannerColorOf(icon: Bitmap): Color {
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0L
    val hsv = FloatArray(3)
    for (y in 0 until icon.height step SAMPLE_STEP) {
        for (x in 0 until icon.width step SAMPLE_STEP) {
            val pixel = icon.getPixel(x, y)
            android.graphics.Color.colorToHSV(pixel, hsv)
            if (android.graphics.Color.alpha(pixel) < OPAQUE || hsv[1] < MIN_SATURATION) continue
            r += android.graphics.Color.red(pixel)
            g += android.graphics.Color.green(pixel)
            b += android.graphics.Color.blue(pixel)
            count++
        }
    }
    if (count == 0L) return NEUTRAL_BANNER
    android.graphics.Color.RGBToHSV((r / count).toInt(), (g / count).toInt(), (b / count).toInt(), hsv)
    hsv[2] = hsv[2].coerceAtMost(MAX_VALUE)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Decoded artwork shared by all tiles, so scrolling back and folder previews don't reload it
 * from the package manager. Bounded by bitmap bytes. Kept per app version ([App.updatedAt]):
 * an update may change the banner, and the old entry just ages out.
 */
private object ArtworkCache {
    private val cache = object : LruCache<Pair<AppId, Long>, Artwork>(CACHE_BYTES) {
        override fun sizeOf(key: Pair<AppId, Long>, value: Artwork): Int = when (value) {
            is Artwork.Banner -> value.image.asAndroidBitmap().byteCount
            is Artwork.Icon -> value.image.asAndroidBitmap().byteCount
            Artwork.None -> 1
        }
    }

    operator fun get(app: App): Artwork? = cache[app.id to app.updatedAt]

    operator fun set(app: App, artwork: Artwork) {
        cache.put(app.id to app.updatedAt, artwork)
    }
}

private fun Drawable.toImage(width: Int, height: Int): ImageBitmap = toBitmap(width, height).asImageBitmap()

private const val BANNER_WIDTH = 320
private const val BANNER_HEIGHT = 180
private const val ICON_SIZE = 96
private const val SAMPLE_STEP = 4
private const val OPAQUE = 200
private const val MIN_SATURATION = 0.25f
private const val MAX_VALUE = 0.45f
private const val COMPACT_ICON = 0.6f
private val NEUTRAL_BANNER = Color(0xFF2A2F3A)
private const val CACHE_BYTES = 24 * 1024 * 1024
