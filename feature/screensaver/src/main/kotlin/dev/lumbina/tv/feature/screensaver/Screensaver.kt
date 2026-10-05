package dev.lumbina.tv.feature.screensaver

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.backdrop.BackdropPhotos
import dev.lumbina.tv.core.domain.home.ClockFormat
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.weather.CurrentWeather
import dev.lumbina.tv.core.domain.weather.ShownWeather
import dev.lumbina.tv.core.domain.weather.WeatherSettingsRepository
import dev.lumbina.tv.core.domain.weather.shownWeather
import dev.lumbina.tv.core.ui.CREDIT_ALPHA
import dev.lumbina.tv.core.ui.CREDIT_MAX_LINES
import dev.lumbina.tv.core.ui.CreditLineBreak
import dev.lumbina.tv.core.ui.GradientTones
import dev.lumbina.tv.core.ui.MINUTE_MILLIS
import dev.lumbina.tv.core.ui.creditLines
import dev.lumbina.tv.core.ui.dateText
import dev.lumbina.tv.core.ui.label
import dev.lumbina.tv.core.ui.rememberMinuteClock
import dev.lumbina.tv.core.ui.temperatureText
import dev.lumbina.tv.core.ui.timeText
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The clock as chosen for home. */
data class ClockLook(val format: ClockFormat = ClockFormat.System, val showDate: Boolean = true)

/** A decoded photo and the credit its source gives, if any. */
class ShownPhoto(val image: ImageBitmap, val credit: String?)

/** What the screensaver shows; shared by the system screensaver and the preview screen. */
class ScreensaverModel @Inject constructor(
    weather: CurrentWeather,
    weatherSettings: WeatherSettingsRepository,
    appearance: HomeAppearanceRepository,
    private val backdrop: BackdropPhotos,
) {

    /** Null while unknown or switched off. */
    val weather: Flow<ShownWeather?> =
        combine(weather.reports(Locale.getDefault().language), weatherSettings.settings, ::shownWeather)

    val clock: Flow<ClockLook> = appearance.settings.map { ClockLook(it.clockFormat, it.showDate) }

    /**
     * A new photo every [PHOTO_MILLIS]. A photo that won't decode (a broken file) is skipped for the
     * next one right away; null only if none of a few would, and a gradient shows instead.
     */
    val photos: Flow<ShownPhoto?> = flow {
        while (true) {
            emit(nextPhoto())
            delay(PHOTO_MILLIS)
        }
    }

    private suspend fun nextPhoto(): ShownPhoto? {
        repeat(PHOTO_ATTEMPTS) {
            val photo = backdrop.next(PHOTO_WIDTH, PHOTO_HEIGHT) ?: return null
            decode(photo.bytes)?.let { return ShownPhoto(it, photo.credit) }
        }
        return null
    }

    private suspend fun decode(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= PHOTO_WIDTH &&
            bounds.outHeight / (sample * 2) >= PHOTO_HEIGHT
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}

internal const val PHOTO_MILLIS = 60_000L
private const val PHOTO_ATTEMPTS = 3
private const val PHOTO_WIDTH = 1920
private const val PHOTO_HEIGHT = 1080

/**
 * Full-screen photos (slow zoom, cross-fades) with the time, date and weather. The text block
 * moves to another corner every few minutes so nothing stays in one place for hours.
 */
@Composable
fun Screensaver(model: ScreensaverModel, modifier: Modifier = Modifier) {
    val photo by model.photos.collectAsStateWithLifecycle(null)
    val report by model.weather.collectAsStateWithLifecycle(null)
    val clock by model.clock.collectAsStateWithLifecycle(ClockLook())
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = photo, animationSpec = tween(FADE_MILLIS), label = "photo") { shown ->
            if (shown != null) SlowZoom(shown.image) else DriftingGradient()
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA))),
            ),
        )
        TimeAndWeather(report, clock, photo?.credit)
    }
}

@Composable
private fun SlowZoom(image: ImageBitmap, modifier: Modifier = Modifier) {
    val zoom by rememberInfiniteTransition(label = "zoom").animateFloat(
        initialValue = 1f,
        targetValue = MAX_ZOOM,
        animationSpec = infiniteRepeatable(
            tween(PHOTO_MILLIS.toInt(), easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "zoom",
    )
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom
            scaleY = zoom
        },
    )
}

@Composable
private fun DriftingGradient(modifier: Modifier = Modifier) {
    val shift by rememberInfiniteTransition(label = "gradient").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GRADIENT_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift",
    )
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.linearGradient(
                colors = GradientTones,
                start = Offset(0f, GRADIENT_SPAN * shift),
                end = Offset(GRADIENT_SPAN, GRADIENT_SPAN * (1 - shift)),
            ),
        ),
    )
}

@Composable
private fun TimeAndWeather(shown: ShownWeather?, clock: ClockLook, credit: String?, modifier: Modifier = Modifier) {
    val now = rememberMinuteClock()
    val startedAt = remember { System.currentTimeMillis() }
    val corner = CORNERS[cornerIndex(now, startedAt, CORNERS.size)]
    // Lines keep to the corner's side: a credit wider than the clock would otherwise push the
    // clock sideways with every photo.
    val atEnd = corner == Alignment.TopEnd || corner == Alignment.BottomEnd
    Box(modifier = modifier.fillMaxSize().padding(64.dp), contentAlignment = corner) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = if (atEnd) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = timeText(now, clock.format),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 112.sp, fontWeight = FontWeight.Light),
                color = Color.White,
            )
            if (clock.showDate) {
                Text(
                    text = dateText(now),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
            }
            shown?.let { (report, fahrenheit) ->
                Text(
                    text = "${report.weather.temperatureText(fahrenheit)} · ${report.weather.condition.label()} · " +
                        report.place.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = TEXT_ALPHA),
                )
            }
            credit?.let {
                Text(
                    text = creditLines(it),
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = CreditLineBreak),
                    color = Color.White.copy(alpha = CREDIT_ALPHA),
                    textAlign = if (atEnd) TextAlign.End else TextAlign.Start,
                    maxLines = CREDIT_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = CREDIT_WIDTH),
                )
            }
        }
    }
}

/** The corner shown at [now]: the first one for the first few minutes after [startedAt], then the next. */
internal fun cornerIndex(now: Long, startedAt: Long, corners: Int): Int =
    ((maxOf(0L, now - startedAt) / (MINUTE_MILLIS * MINUTES_PER_CORNER)) % corners).toInt()

private val CORNERS = listOf(Alignment.BottomStart, Alignment.TopEnd, Alignment.BottomEnd, Alignment.TopStart)
private const val MINUTES_PER_CORNER = 5
private const val FADE_MILLIS = 2_000
private const val GRADIENT_MILLIS = 40_000
private const val GRADIENT_SPAN = 2_000f
private const val MAX_ZOOM = 1.08f
private const val SCRIM_ALPHA = 0.6f
private const val TEXT_ALPHA = 0.9f
private val CREDIT_WIDTH = 560.dp
