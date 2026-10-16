package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.ui.WallpaperLayer
import dev.lumbina.tv.core.ui.colors

/**
 * The wallpaper behind all of Lumbina's screens, with its photo's credit; the screens over it dim
 * it themselves. A video holds still while [busy].
 */
@Composable
fun WallpaperBackdrop(busy: Boolean, modifier: Modifier = Modifier, viewModel: WallpaperViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize()) {
        val ground = state.background.colors(LocalTheme.current)
        WallpaperLayer(state.item, ground, state.nightDim, busy = busy, credit = true)
    }
}
