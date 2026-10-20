package dev.lumbina.tv.core.ui

import android.content.Context
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperKind
import dev.lumbina.tv.core.domain.theme.ThemeRole
import kotlinx.coroutines.delay

/**
 * The home screen's wallpaper: [fallback] (a gradient) until and unless there is an [item];
 * pictures and videos cross-fade and get the theme's veil so tiles and text stay readable, [nightDim]
 * darker still at night. A video holds still while [busy] (the user is moving around): on TV
 * chips, composing a playing video under a scrolling screen costs whole frames. The item's credit,
 * if it has one, is written small in the bottom corner for a while after the item appears, unless
 * [credit] is off (another screen lies over the wallpaper).
 */
@Composable
fun WallpaperLayer(
    item: WallpaperItem?,
    fallback: List<Color>,
    nightDim: Float,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    credit: Boolean = true,
    onVideoError: (String, VideoFailure) -> Unit = { _, _ -> },
) {
    Box(modifier.fillMaxSize().ditheredGradient(fallback)) {
        Crossfade(targetState = item, animationSpec = tween(CROSSFADE_MILLIS), label = "wallpaper") { current ->
            when (current?.kind) {
                WallpaperKind.Image -> AsyncImage(
                    model = current.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                WallpaperKind.Video -> MutedVideo(current.uri, busy = busy, onError = { onVideoError(current.uri, it) })

                WallpaperKind.Color -> Box(Modifier.fillMaxSize().background(colorOf(current.uri)))

                null -> Unit
            }
        }
        if (item != null) {
            // The theme's veil: thin at the top, where the picture is looked at, dense down where
            // the rows of tiles and their names are read. Night's own darkening lies over it.
            val theme = LocalTheme.current
            val veil = theme.colorOf(ThemeRole.Veil)
            val gradient = Brush.verticalGradient(
                listOf(veil.copy(alpha = theme.veilTop), veil.copy(alpha = theme.veilBottom)),
            )
            Box(Modifier.fillMaxSize().background(gradient))
            if (nightDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = nightDim)))
        }
        Credit(item, credit, Modifier.align(Alignment.BottomEnd).padding(horizontal = 48.dp, vertical = 16.dp))
    }
}

/**
 * The [item]'s credit, for a while after the item appears and only while [uncovered]: text that
 * stays in one place marks OLED screens. Coming back to the wallpaper doesn't show it again.
 */
@Composable
private fun Credit(item: WallpaperItem?, uncovered: Boolean, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(item?.uri) {
        val credit = item?.credit
        if (credit != null) {
            shown = credit
            visible = true
            delay(CREDIT_MILLIS)
        }
        visible = false
    }
    val text: @Composable () -> Unit = {
        Text(
            text = creditLines(shown.orEmpty()),
            style = MaterialTheme.typography.labelSmall.copy(lineBreak = CreditLineBreak),
            color = Color.White.copy(alpha = CREDIT_ALPHA),
            textAlign = TextAlign.End,
            maxLines = CREDIT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = CREDIT_WIDTH),
        )
    }
    val showing = visible && uncovered
    if (!LAYERS_ARE_CHEAP) {
        // A fade is a layer for as long as the text shows.
        if (showing) Box(modifier) { text() }
        return
    }
    AnimatedVisibility(
        showing,
        enter = fadeIn(tween(CROSSFADE_MILLIS)),
        exit = fadeOut(tween(CROSSFADE_MILLIS)),
        modifier = modifier,
    ) { text() }
}

/**
 * Why a video stopped, by the player's [code]. [lasting]: the file itself won't play on this device
 * (a format it has no decoder for, a broken container); otherwise it may play another time, as
 * when another video still holds the only decoder.
 */
@OptIn(UnstableApi::class)
class VideoFailure(val code: String, val lasting: Boolean) {
    constructor(error: PlaybackException) : this(error.errorCodeName, error.errorCode in LASTING)

    private companion object {
        val LASTING = setOf(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        )
    }
}

/**
 * A silent video filling the screen, looping unless played [once] ([onEnd] then). It never asks
 * for audio focus, so it doesn't pause music or another app's sound, and it stops while its screen
 * is not visible or [busy]. [onError]: the player stopped on an error. Looping, it tries again a
 * while after a passing error (another player held the decoder, the network went); played once, a
 * video stuck loading counts as an error too, rather than a frozen frame for minutes.
 */
@OptIn(UnstableApi::class)
@Composable
fun MutedVideo(
    uri: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    once: Boolean = false,
    onEnd: () -> Unit = {},
    onError: (VideoFailure) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ended by rememberUpdatedState(onEnd)
    val failed by rememberUpdatedState(onError)
    var passingFailures by remember(uri) { mutableIntStateOf(0) }
    val player = remember(uri) {
        mutedPlayer(context, uri, once, onEnd = { ended() }) { failure ->
            failed(failure)
            if (!once && !failure.lasting) passingFailures++
        }
    }
    LaunchedEffect(player, passingFailures) {
        if (passingFailures == 0) return@LaunchedEffect
        delay(RETRY_MILLIS)
        player.prepare()
    }
    if (once) LaunchedEffect(player) { giveUpWhenStuck(player) { failed(it) } }
    val latestBusy by rememberUpdatedState(busy)
    LaunchedEffect(player, busy) {
        if (busy) {
            player.pause()
        } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            player.play()
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!latestBusy) player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    // A TextureView, not a SurfaceView: on some TV chips a video plane under the UI makes every
    // UI frame slow; composing the video in the app's layer does not.
    AndroidView(
        factory = { context ->
            AspectRatioFrameLayout(context).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                val texture = TextureView(context)
                addView(texture, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                player.setVideoTextureView(texture)
                player.addListener(
                    object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            if (videoSize.height > 0) {
                                setAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                            }
                        }
                    },
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

@OptIn(UnstableApi::class)
private fun mutedPlayer(
    context: Context,
    uri: String,
    once: Boolean,
    onEnd: () -> Unit,
    onError: (VideoFailure) -> Unit,
): ExoPlayer = ExoPlayer.Builder(context).build().apply {
    setMediaItem(MediaItem.fromUri(uri))
    repeatMode = if (once) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
    volume = 0f
    addListener(
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                // Done with: the decoder goes now, not after the cross-fade, for the next video.
                stop()
                onEnd()
            }

            override fun onPlayerError(error: PlaybackException) = onError(VideoFailure(error))
        },
    )
    prepare()
}

/** Loading this long while meant to play: stuck (a stalled stream), as good as failed. */
private suspend fun giveUpWhenStuck(player: ExoPlayer, onStuck: (VideoFailure) -> Unit) {
    var loadingSince = 0L
    while (true) {
        delay(STALL_CHECK_MILLIS)
        val loading = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
        loadingSince = if (loading) loadingSince.takeIf { it > 0 } ?: System.currentTimeMillis() else 0L
        if (loadingSince > 0 && System.currentTimeMillis() - loadingSince > STALL_MILLIS) {
            player.stop()
            return onStuck(VideoFailure(STALLED, lasting = false))
        }
    }
}

private fun colorOf(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.Black)

private const val CROSSFADE_MILLIS = 1_200
private const val RETRY_MILLIS = 15_000L
private const val STALL_CHECK_MILLIS = 5_000L
private const val STALL_MILLIS = 30_000L
private const val STALLED = "STALLED"

/**
 * A photo's credit as it is written: what it shows on one line and whose it is on the next, where
 * the credit ends in "(© ...)" as Bing's do. Left to wrap by width, the last word of the bracket
 * hung alone on the second line.
 */
fun creditLines(credit: String): String {
    val match = CREDIT_OWNER.find(credit) ?: return credit
    return match.groupValues[1] + "\n" + match.groupValues[2]
}

private val CREDIT_OWNER = Regex("""^(.+?)\s*\((©[^()]*)\)$""")

/** A credit's lines of text share its words evenly, so none hangs alone. */
val CreditLineBreak = LineBreak.Heading

/** How faint a photo's credit is written, on the wallpaper and on the screensaver. */
const val CREDIT_ALPHA = 0.6f
private const val CREDIT_MILLIS = 20_000L
private val CREDIT_WIDTH = 640.dp

/** What it shows, on two lines at most, and whose it is. */
const val CREDIT_MAX_LINES = 3
