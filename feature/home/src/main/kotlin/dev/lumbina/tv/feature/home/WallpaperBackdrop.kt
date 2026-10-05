package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lumbina.tv.core.ui.WallpaperLayer
import dev.lumbina.tv.core.ui.colors

/**
 * The wallpaper behind all of Lumbina's screens: plain on home, [dim]med behind settings and the
 * app list so their text stays readable. A video holds still while [busy].
 */
@Composable
fun WallpaperBackdrop(
    dim: Float,
    busy: Boolean,
    modifier: Modifier = Modifier,
    viewModel: WallpaperViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize()) {
        WallpaperLayer(state.item, state.background.colors(), state.nightDim, busy = busy, credit = dim == 0f)
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}
