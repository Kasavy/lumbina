package dev.lumbina.tv.core.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
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
 * [named]: the tile is to show the name on itself, at its bottom over a shade as on any banner; a
 * banner made from the icon then shows the icon alone, a little up, so the name is not there twice.
 */
@Composable
fun AppArtwork(app: App, modifier: Modifier = Modifier, compact: Boolean = false, named: Boolean = false) {
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
            is Artwork.Banner -> Picture(
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
                    Picture(
                        bitmap = artwork.image,
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxSize(COMPACT_ICON),
                    )
                } else if (named) {
                    Picture(
                        bitmap = artwork.image,
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxHeight(NAMED_ICON).aspectRatio(1f).offset(y = (-12).dp),
                    )
                } else {
                    IconWithLabel(artwork.image, app.label)
                }
            }

            Artwork.None -> if (!compact) Label(app.label)
        }
        if (named && artwork !is Artwork.None) NameOnTile(app.label)
    }
}

/**
 * The icon with the name beside it; where a word of the name would not fit beside the icon (a
 * picker's small tiles), the name goes under the icon on one line instead of breaking the word.
 */
@Composable
private fun IconWithLabel(icon: ImageBitmap, label: String) = BoxWithConstraints(contentAlignment = Alignment.Center) {
    val style = MaterialTheme.typography.titleSmall.onTile()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val room = maxWidth - BESIDE_ICON
    val wordFits = remember(label, style, room) {
        val widest = label.split(' ').maxOf { measurer.measure(it, style).size.width }
        with(density) { widest.toDp() } <= room
    }
    if (!wordFits) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Picture(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.onTile(),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return@BoxWithConstraints
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Picture(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(
            text = label,
            style = style,
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
        style = MaterialTheme.typography.labelLarge.onTile(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/**
 * Loads the artwork of [apps] not loaded yet, off the main thread, before their tiles are seen: a
 * screen of them scrolled into view for the first time otherwise decodes a row of pictures at once,
 * and a weak box drops frames doing it.
 */
suspend fun preloadArtwork(context: Context, apps: List<App>) = withContext(Dispatchers.IO) {
    apps.filter { ArtworkCache[it] == null }.forEach { app ->
        ArtworkCache[app] = context.packageManager.loadArtwork(app)
    }
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
private const val NAMED_ICON = 0.45f

/** What the icon takes beside the name: the padding at both sides, the icon and the gap. */
private val BESIDE_ICON = 12.dp * 2 + 40.dp + 10.dp
private val NEUTRAL_BANNER = Color(0xFF2A2F3A)
private const val CACHE_BYTES = 24 * 1024 * 1024
