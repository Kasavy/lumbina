package dev.lumbina.tv.feature.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.line

/** A plate at the left of the top line while some app plays (or has paused) media; nothing otherwise. */
@Composable
internal fun NowPlayingRoute(modifier: Modifier = Modifier, viewModel: NowPlayingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    // A skip button the player no longer takes stays while it has the focus: gone from under it,
    // the focus would go nowhere. It goes once the focus moves on.
    var onPrevious by remember { mutableStateOf(false) }
    var onNext by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = BAR_ALPHA), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f, fill = false).widthIn(max = 640.dp)) {
            Text(
                text = current.playing.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = line(current.playing.artist, current.app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current.playing.canPrevious || onPrevious) {
            SkipButton(R.drawable.ic_skip_previous, R.string.media_previous, viewModel::previous) { onPrevious = it }
        }
        IconButton(onClick = viewModel::playPause) {
            val playing = current.playing.playing
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (playing) R.string.media_pause else R.string.media_play),
            )
        }
        if (current.playing.canNext || onNext) {
            SkipButton(R.drawable.ic_skip_next, R.string.media_next, viewModel::next) { onNext = it }
        }
    }
}

@Composable
private fun SkipButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit, onFocus: (Boolean) -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.onFocusChanged { onFocus(it.isFocused) }) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

private const val BAR_ALPHA = 0.6f
