package dev.lumbina.tv.feature.screensaver

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.BackdropPhoto
import dev.lumbina.tv.core.domain.backdrop.BackdropPhotos
import dev.lumbina.tv.core.domain.backdrop.MarkUnplayable
import dev.lumbina.tv.core.domain.home.ClockFormat
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.phone.LocalAddress
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettingsRepository
import dev.lumbina.tv.core.domain.phone.phoneRemoteAddress
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.weather.CurrentWeather
import dev.lumbina.tv.core.domain.weather.ShownWeather
import dev.lumbina.tv.core.domain.weather.WeatherSettingsRepository
import dev.lumbina.tv.core.domain.weather.shownWeather
import dev.lumbina.tv.core.ui.CREDIT_ALPHA
import dev.lumbina.tv.core.ui.CREDIT_MAX_LINES
import dev.lumbina.tv.core.ui.CreditLineBreak
import dev.lumbina.tv.core.ui.GradientTones
import dev.lumbina.tv.core.ui.LAYERS_ARE_CHEAP
import dev.lumbina.tv.core.ui.MINUTE_MILLIS
import dev.lumbina.tv.core.ui.MutedVideo
import dev.lumbina.tv.core.ui.QrCode
import dev.lumbina.tv.core.ui.VideoFailure
import dev.lumbina.tv.core.ui.creditLines
import dev.lumbina.tv.core.ui.dateText
import dev.lumbina.tv.core.ui.label
import dev.lumbina.tv.core.ui.line
import dev.lumbina.tv.core.ui.rememberMinuteClock
import dev.lumbina.tv.core.ui.temperatureText
import dev.lumbina.tv.core.ui.timeText
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The clock as chosen for home. */
data class ClockLook(val format: ClockFormat = ClockFormat.System, val showDate: Boolean = true)

/** A decoded photo and the credit its source gives, if any; or a [video] to play through. */
class ShownPhoto(val image: ImageBitmap?, val credit: String?, val video: String? = null)

/** What the screensaver shows; shared by the system screensaver and the preview screen. */
class ScreensaverModel @Inject constructor(
    weather: CurrentWeather,
    weatherSettings: WeatherSettingsRepository,
    appearance: HomeAppearanceRepository,
    private val backdrop: BackdropPhotos,
    private val sleep: SleepSettingsRepository,
    phoneRemote: PhoneRemoteSettingsRepository,
    address: LocalAddress,
    private val markUnplayable: MarkUnplayable,
    private val log: LumbinaLog,
) {

    /**
     * The phone remote page's address, to show as a code: only where the user asked for it on the
     * screensaver, the page is on and the device has a network address. Null otherwise. The
     * address is asked again every minute: the network may come, or change, while the screensaver shows.
     */
    val remoteCode: Flow<String?> = combine(phoneRemote.settings, minutes()) { remote, _ ->
        if (remote.enabled && remote.onScreensaver) phoneRemoteAddress(address.ipv4(), remote.key) else null
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun minutes(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(ADDRESS_MILLIS)
        }
    }

    /** Null while unknown or switched off. */
    val weather: Flow<ShownWeather?> =
        combine(weather.reports(Locale.getDefault().language), weatherSettings.settings, ::shownWeather)

    val clock: Flow<ClockLook> = appearance.settings.map { ClockLook(it.clockFormat, it.showDate) }

    /** Said by the screen when a video has played through, or could not be played. */
    private val videoDone = Channel<Unit>(Channel.CONFLATED)

    /** Only photos until then: a video failed, and the next may fail the same way (offline, no decoder). */
    @Volatile private var videosFrom = 0L

    /**
     * A new photo as often as the settings say; a video stays until it has played through
     * ([videoEnded]), at most [VIDEO_MILLIS]. A photo that won't decode (a broken file) is skipped
     * for the next one right away; null only if none of a few would, and a gradient shows instead.
     */
    val photos: Flow<ShownPhoto?> = flow {
        while (true) {
            val shown = nextPhoto()
            // An end said for the video before this one is not this one's.
            videoDone.tryReceive()
            emit(shown)
            if (shown?.video != null) {
                withTimeoutOrNull(VIDEO_MILLIS) { videoDone.receive() }
            } else {
                delay(sleep.settings.first().screensaverChangeMinutes * MINUTE_MILLIS)
            }
        }
    }

    fun videoEnded() {
        videoDone.trySend(Unit)
    }

    /**
     * The player stopped on [uri]: a photo comes now, and only photos for [VIDEO_PAUSE_MILLIS]. One of
     * the user's own that won't play on this device is marked so and skipped from then on; after a
     * passing failure it comes again in its turn.
     */
    suspend fun videoFailed(uri: String, failure: VideoFailure) {
        log.log(TAG, "video failed: ${failure.code}${if (failure.lasting) ", marked" else ""}")
        videosFrom = System.currentTimeMillis() + VIDEO_PAUSE_MILLIS
        if (failure.lasting) withContext(Dispatchers.IO) { markUnplayable(uri, System.currentTimeMillis()) }
        videoEnded()
    }

    private suspend fun nextPhoto(): ShownPhoto? {
        repeat(PHOTO_ATTEMPTS) {
            val photo = backdrop.next(PHOTO_WIDTH, PHOTO_HEIGHT, videos = System.currentTimeMillis() >= videosFrom)
                ?: return null
            when (photo) {
                is BackdropPhoto.Video -> return ShownPhoto(null, photo.credit, video = photo.uri)
                is BackdropPhoto.Image -> decode(photo.bytes)?.let { return ShownPhoto(it, photo.credit) }
            }
        }
        return null
    }

    // Where layers are dear, the weak boxes, memory is short too: two bytes a pixel.
    private suspend fun decode(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
        decodeToCover(bytes, PHOTO_WIDTH, PHOTO_HEIGHT, lean = !LAYERS_ARE_CHEAP)?.asImageBitmap()
    }
}

/** One way of a photo's slow zoom, there and back again for as long as it shows. */
private const val ZOOM_MILLIS = 60_000
private const val TAG = "screensaver"

/** A long video gives way after this all the same. */
private const val VIDEO_MILLIS = 10 * 60_000L
private const val VIDEO_PAUSE_MILLIS = 5 * 60_000L
private const val PHOTO_ATTEMPTS = 3
private const val PHOTO_WIDTH = 1920
private const val PHOTO_HEIGHT = 1080

/**
 * Full-screen photos (slow zoom, cross-fades) with the time, date and weather, and the phone
 * remote's code in the opposite corner when it is to be shown there. The text block moves to
 * another corner every few minutes so nothing stays in one place for hours.
 */
@Composable
fun Screensaver(model: ScreensaverModel, modifier: Modifier = Modifier) {
    val photo by model.photos.collectAsStateWithLifecycle(null)
    val report by model.weather.collectAsStateWithLifecycle(null)
    val clock by model.clock.collectAsStateWithLifecycle(ClockLook())
    val remoteCode by model.remoteCode.collectAsStateWithLifecycle(null)
    val now = rememberMinuteClock()
    val startedAt = remember { System.currentTimeMillis() }
    val corner = cornerIndex(now, startedAt, CORNERS.size)
    val scope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = photo, animationSpec = tween(FADE_MILLIS), label = "photo") { shown ->
            val video = shown?.video
            val image = shown?.image
            when {
                video != null -> MutedVideo(
                    video,
                    once = true,
                    onEnd = model::videoEnded,
                    onError = { scope.launch { model.videoFailed(video, it) } },
                )

                image != null -> SlowZoom(image)

                else -> DriftingGradient()
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA))),
            ),
        )
        TimeAndWeather(report, clock, photo?.credit, now, CORNERS[corner])
        // Across from the clock, and moving with it: neither stays in one place for hours.
        remoteCode?.let { RemoteCode(it, CORNERS[(corner + CORNERS.size / 2) % CORNERS.size]) }
    }
}

@Composable
private fun SlowZoom(image: ImageBitmap, modifier: Modifier = Modifier) {
    val zoom by rememberInfiniteTransition(label = "zoom").animateFloat(
        initialValue = 1f,
        targetValue = MAX_ZOOM,
        animationSpec = infiniteRepeatable(
            tween(ZOOM_MILLIS, easing = LinearEasing),
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

/** The phone remote page as a code to point a camera at; no address in letters: nobody types one from a sofa. */
@Composable
private fun RemoteCode(address: String, corner: Alignment, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(64.dp), contentAlignment = corner) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            QrCode(address, size = CODE_SIZE)
            Text(
                stringResource(R.string.screensaver_phone_remote),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = TEXT_ALPHA),
            )
        }
    }
}

@Composable
private fun TimeAndWeather(
    shown: ShownWeather?,
    clock: ClockLook,
    credit: String?,
    now: Long,
    corner: Alignment,
    modifier: Modifier = Modifier,
) {
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
                    text = line(
                        report.weather.temperatureText(fahrenheit),
                        report.weather.condition.label(),
                        report.place.name,
                    ),
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

// Each corner's opposite is two places on: the clock and the phone remote's code stand across from each other.
private val CORNERS = listOf(Alignment.BottomStart, Alignment.BottomEnd, Alignment.TopEnd, Alignment.TopStart)
private val CODE_SIZE = 90.dp
private const val MINUTES_PER_CORNER = 5
private const val FADE_MILLIS = 2_000
private const val GRADIENT_MILLIS = 40_000
private const val GRADIENT_SPAN = 2_000f
private const val MAX_ZOOM = 1.08f
private const val SCRIM_ALPHA = 0.6f
private const val TEXT_ALPHA = 0.9f
private val CREDIT_WIDTH = 560.dp

/** How often the device's address is asked for the phone remote's code. */
private const val ADDRESS_MILLIS = 60_000L
