package dev.lumbina.tv.core.ui

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.domain.theme.ThemeSpec
import dev.lumbina.tv.core.domain.theme.Themes
import kotlin.random.Random

/** The gradient as [theme] has it: quiet, so that banners and text stay readable on it. */
fun HomeBackground.brush(theme: ThemeSpec): Brush = Brush.verticalGradient(colors(theme))

/**
 * The gradient's colors in [theme], top to bottom, evenly spaced: its tint at the top, the theme's
 * own background at the bottom. The light theme has pale ones, the black theme deeper ones that
 * end in black; any other, the dark ones.
 */
fun HomeBackground.colors(theme: ThemeSpec): List<Color> {
    val tones = when (theme.id) {
        Themes.Light.id -> LightTones
        Themes.Black.id -> BlackTones
        else -> DarkTones
    }
    return tones.getValue(this) + theme.colorOf(ThemeRole.Background)
}

/**
 * Fills the background with a vertical gradient of [colors], dithered. These gradients cross a
 * dozen shades over the whole screen, and a TV shows each shade as a horizontal band; Android 9
 * doesn't dither gradients even when asked. So the gradient is worked out once into a strip as
 * tall as the screen, each pixel rounded up or down at random, and the strip is repeated across.
 * The strip is kept ([DitheredStrips]): the background is composed again whenever the wallpaper
 * layer is, and working a megabyte of pixels out each time would land in the frame of a key press.
 */
fun Modifier.ditheredGradient(colors: List<Color>): Modifier = drawWithCache {
    val strip = DitheredStrips.of(colors, size.height.toInt().coerceAtLeast(1))
    val paint = Paint().apply { shader = ImageShader(strip, TileMode.Repeated, TileMode.Clamp) }
    onDrawBehind { drawIntoCanvas { it.drawRect(0f, 0f, size.width, size.height, paint) } }
}

/** The last strip made: one background at one height is all a screen shows. Main thread. */
private object DitheredStrips {
    private var colors: List<Color>? = null
    private var height = 0
    private var strip: ImageBitmap? = null

    fun of(colors: List<Color>, height: Int): ImageBitmap {
        strip?.takeIf { this.colors == colors && this.height == height }?.let { return it }
        val pixels = ditheredPixels(colors, height)
        return Bitmap.createBitmap(pixels, STRIP_WIDTH, height, Bitmap.Config.ARGB_8888).asImageBitmap().also {
            this.colors = colors
            this.height = height
            strip = it
        }
    }
}

/** [STRIP_WIDTH] × [height] pixels of the gradient, row after row. */
internal fun ditheredPixels(colors: List<Color>, height: Int): IntArray {
    val pixels = IntArray(STRIP_WIDTH * height)
    // The same noise every time: the background must not flicker when it is drawn again.
    val random = Random(STRIP_SEED)
    val last = (colors.size - 1).coerceAtLeast(1)
    for (y in 0 until height) {
        val at = y.toFloat() / (height - 1).coerceAtLeast(1) * last
        val from = colors[at.toInt().coerceAtMost(colors.size - 1)]
        val to = colors[(at.toInt() + 1).coerceAtMost(colors.size - 1)]
        // Mixed here, channel by channel: a Color keeps only 256 shades, the very steps to smooth out.
        val part = at - at.toInt()
        val red = from.red + (to.red - from.red) * part
        val green = from.green + (to.green - from.green) * part
        val blue = from.blue + (to.blue - from.blue) * part
        for (x in 0 until STRIP_WIDTH) {
            // One roll for the three channels: the noise is in brightness, not in color.
            val roll = random.nextFloat()
            pixels[y * STRIP_WIDTH + x] = OPAQUE or
                (shade(red, roll) shl RED_SHIFT) or
                (shade(green, roll) shl GREEN_SHIFT) or
                shade(blue, roll)
        }
    }
    return pixels
}

/** [channel] (0..1) as one of 256 shades: the nearer of the two around it more often, by [roll]. */
private fun shade(channel: Float, roll: Float): Int = (channel * MAX_SHADE + roll).toInt().coerceIn(0, MAX_SHADE)

internal const val STRIP_WIDTH = 256
private const val STRIP_SEED = 7
private const val MAX_SHADE = 255
private const val OPAQUE = 0xFF shl 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/** The deep tones of the home gradients, for backgrounds that drift between them. */
val GradientTones: List<Color> get() = listOf(OceanTop, PlumTop, ForestTop)

private val NightTop = Color(0xFF151A22)
private val DuskTop = Color(0xFF3A2238)
private val DuskMiddle = Color(0xFF1D1B2E)
private val OceanTop = Color(0xFF0D2F45)
private val OceanMiddle = Color(0xFF0B1B28)
private val ForestTop = Color(0xFF15372A)
private val ForestMiddle = Color(0xFF0F2219)
private val PlumTop = Color(0xFF3C1846)
private val PlumMiddle = Color(0xFF221028)

// Each gradient's tones above the theme's background, top first.
private val DarkTones = mapOf(
    HomeBackground.Night to listOf(NightTop),
    HomeBackground.Dusk to listOf(DuskTop, DuskMiddle),
    HomeBackground.Ocean to listOf(OceanTop, OceanMiddle),
    HomeBackground.Forest to listOf(ForestTop, ForestMiddle),
    HomeBackground.Plum to listOf(PlumTop, PlumMiddle),
)

// Pale tints of the same hues, light enough for the light theme's dark text.
private val LightTones = mapOf(
    HomeBackground.Night to listOf(Color(0xFFD5DBE3)),
    HomeBackground.Dusk to listOf(Color(0xFFEBD3E0), Color(0xFFE4DEEA)),
    HomeBackground.Ocean to listOf(Color(0xFFC8DEEB), Color(0xFFDBE7EE)),
    HomeBackground.Forest to listOf(Color(0xFFCDE2D4), Color(0xFFDEEAE1)),
    HomeBackground.Plum to listOf(Color(0xFFE1CDEA), Color(0xFFEADFEE)),
)

// The dark ones deeper, ending in black, for the black theme.
private val BlackTones = mapOf(
    HomeBackground.Night to listOf(Color(0xFF0B0E13)),
    HomeBackground.Dusk to listOf(Color(0xFF26142A), Color(0xFF110F1C)),
    HomeBackground.Ocean to listOf(Color(0xFF07202F), Color(0xFF060F17)),
    HomeBackground.Forest to listOf(Color(0xFF0B2419), Color(0xFF07140E)),
    HomeBackground.Plum to listOf(Color(0xFF28102F), Color(0xFF130816)),
)
