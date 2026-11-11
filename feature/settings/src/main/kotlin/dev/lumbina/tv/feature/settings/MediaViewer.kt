package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.ui.LumbinaPopup
import dev.lumbina.tv.core.ui.MutedVideo
import dev.lumbina.tv.core.ui.requestFocusSoon

/**
 * A file over the whole screen, on black: a photo whole, a video playing whole and looping, silent
 * as on the wallpaper and screensaver. Back closes it ([onClose]), and a click of a mouse.
 */
@Composable
internal fun MediaViewer(entry: MediaEntry, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LumbinaPopup(onDismissRequest = onClose) {
        // Holds the focus, so the keys come here and Back closes this, not the library; a mouse closes it by a click.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .semantics {
                    onClick {
                        onClose()
                        true
                    }
                },
        ) {
            if (entry.file.kind == MediaKind.Video) {
                // Its frame until it plays, and if it won't.
                AsyncImage(
                    model = picture(entry),
                    contentDescription = entry.file.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                var playing by remember { mutableStateOf(false) }
                MutedVideo(
                    entry.file.uri,
                    Modifier.graphicsLayer { alpha = if (playing) 1f else 0f },
                    fit = true,
                    onFirstFrame = { playing = true },
                )
            } else {
                AsyncImage(
                    model = entry.file.uri,
                    contentDescription = entry.file.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocusSoon() }
}
