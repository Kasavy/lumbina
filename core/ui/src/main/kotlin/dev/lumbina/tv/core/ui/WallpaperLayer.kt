package dev.lumbina.tv.core.ui

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
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperKind
import kotlinx.coroutines.delay

/**
 * The home screen's wallpaper: [fallback] (a gradient) until and unless there is an [item];
 * pictures and videos cross-fade and get a dark veil so tiles and text stay readable, [nightDim]
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

                WallpaperKind.Video -> VideoWallpaper(current.uri, busy)

                WallpaperKind.Color -> Box(Modifier.fillMaxSize().background(colorOf(current.uri)))

                null -> Unit
            }
        }
        if (item != null) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = TOP_VEIL + nightDim),
                            Color.Black.copy(alpha = MIDDLE_VEIL + nightDim),
                            Color.Black.copy(alpha = BOTTOM_VEIL + nightDim),
                        ),
                    ),
                ),
            )
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
 * A silent looping video. It never asks for audio focus, so it doesn't pause music or another
 * app's sound, and it stops while the home screen is not visible.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoWallpaper(uri: String, busy: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
        }
    }
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

private fun colorOf(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.Black)

private const val CROSSFADE_MILLIS = 1_200
private const val TOP_VEIL = 0.55f
private const val MIDDLE_VEIL = 0.3f
private const val BOTTOM_VEIL = 0.6f

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
